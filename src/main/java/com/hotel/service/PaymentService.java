package com.hotel.service;

import com.hotel.model.Booking;
import com.hotel.model.PaymentOrder;
import com.hotel.model.PaymentOrderStatus;
import com.hotel.model.PaymentStatus;
import com.hotel.model.Room;
import com.hotel.repository.BookingRepository;
import com.hotel.repository.PaymentOrderRepository;
import com.hotel.repository.RoomRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class PaymentService {

	private final RoomRepository roomRepository;
	private final BookingRepository bookingRepository;
	private final PaymentOrderRepository paymentOrderRepository;
	private final String keyId;
	private final String keySecret;
	private final BigDecimal gstRatePercent;
	private final boolean testMode;
	private final RestClient razorpayClient;

	public PaymentService(
			RoomRepository roomRepository,
			BookingRepository bookingRepository,
			PaymentOrderRepository paymentOrderRepository,
			@Value("${razorpay.key-id:test_mode}") String keyId,
			@Value("${razorpay.key-secret:test_mode}") String keySecret,
			@Value("${app.payment.gst-rate-percent:18}") BigDecimal gstRatePercent) {
		this.roomRepository = roomRepository;
		this.bookingRepository = bookingRepository;
		this.paymentOrderRepository = paymentOrderRepository;
		this.keyId = keyId;
		this.keySecret = keySecret;
		this.gstRatePercent = gstRatePercent;
		boolean testKeyId = "test_mode".equalsIgnoreCase(keyId);
		boolean testKeySecret = "test_mode".equalsIgnoreCase(keySecret);
		if (testKeyId != testKeySecret || keyId.isBlank() || keySecret.isBlank()) {
			throw new IllegalStateException("Set both Razorpay keys or set both to test_mode");
		}
		this.testMode = testKeyId;
		if (gstRatePercent.signum() <= 0 || gstRatePercent.compareTo(BigDecimal.valueOf(100)) > 0) {
			throw new IllegalStateException("HOTEL_GST_RATE must be greater than 0 and no more than 100");
		}
		this.razorpayClient = RestClient.builder()
				.baseUrl("https://api.razorpay.com/v1")
				.defaultHeaders(headers -> headers.setBasicAuth(keyId, keySecret))
				.build();
	}

	@Transactional
	public CheckoutOrderResponse createOrder(CreatePaymentRequest request, String userEmail) {
		validateRequest(request, userEmail);
		Room room = roomRepository.findByIdForUpdate(request.roomId())
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Room not found"));
		if (!room.isAvailable()
				|| roomRepository.findAvailableBetween(
						request.checkInDate(),
						request.checkOutDate(),
						PaymentOrderStatus.PENDING)
				.stream()
				.noneMatch(availableRoom -> availableRoom.getId().equals(room.getId()))) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "Room is not available for the selected dates");
		}

		long nights = ChronoUnit.DAYS.between(request.checkInDate(), request.checkOutDate());
		BigDecimal subtotal = room.getPricePerNight().multiply(BigDecimal.valueOf(nights)).setScale(2);
		BigDecimal gstAmount = subtotal.multiply(gstRatePercent)
				.divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
		BigDecimal totalAmount = subtotal.add(gstAmount).setScale(2);
		String orderId = testMode ? "order_test_" + UUID.randomUUID().toString().replace("-", "")
				: createRazorpayOrder(totalAmount);

		PaymentOrder paymentOrder = new PaymentOrder();
		paymentOrder.setOrderId(orderId);
		paymentOrder.setUserEmail(userEmail.toLowerCase(Locale.ROOT));
		paymentOrder.setGuestName(request.guestName().trim());
		paymentOrder.setGuestEmail(request.guestEmail().trim().toLowerCase(Locale.ROOT));
		paymentOrder.setGuestPhone(request.guestPhone().trim());
		paymentOrder.setCheckInDate(request.checkInDate());
		paymentOrder.setCheckOutDate(request.checkOutDate());
		paymentOrder.setSubtotal(subtotal);
		paymentOrder.setGstAmount(gstAmount);
		paymentOrder.setTotalAmount(totalAmount);
		paymentOrder.setStatus(PaymentOrderStatus.PENDING);
		paymentOrder.setRoom(room);
		paymentOrderRepository.save(paymentOrder);

		return new CheckoutOrderResponse(
				orderId,
				testMode ? null : keyId,
				toPaise(totalAmount),
				subtotal,
				gstAmount,
				totalAmount,
				gstRatePercent,
				"INR",
				testMode);
	}

	@Transactional
	public PaymentVerificationResponse verifyPayment(VerifyPaymentRequest request, String userEmail) {
		if (request == null || request.orderId() == null || request.orderId().isBlank()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "orderId is required");
		}
		PaymentOrder order = paymentOrderRepository.findByOrderIdForUpdate(request.orderId())
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Payment order not found"));
		if (!order.getUserEmail().equalsIgnoreCase(userEmail)) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "This payment order belongs to another account");
		}
		if (order.getStatus() == PaymentOrderStatus.CANCELLED) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "Payment order was cancelled");
		}
		if (order.getStatus() == PaymentOrderStatus.PAID) {
			return toVerificationResponse(order);
		}

		String paymentId = testMode
				? verifySimulatedPayment(request)
				: verifyRazorpayPayment(request, order);
		Room room = order.getRoom();

		Booking booking = new Booking();
		booking.setGuestName(order.getGuestName());
		booking.setGuestEmail(order.getGuestEmail());
		booking.setGuestPhone(order.getGuestPhone());
		booking.setCheckInDate(order.getCheckInDate());
		booking.setCheckOutDate(order.getCheckOutDate());
		booking.setTotalPrice(order.getTotalAmount());
		booking.setStatus("CONFIRMED");
		booking.setPaymentStatus(PaymentStatus.PAID);
		booking.setPaymentId(paymentId);
		booking.setRoom(room);
		Booking savedBooking = bookingRepository.save(booking);

		order.setStatus(PaymentOrderStatus.PAID);
		order.setPaymentId(paymentId);
		order.setBooking(savedBooking);
		paymentOrderRepository.save(order);
		return toVerificationResponse(order);
	}

	@Transactional
	public void cancelOrder(String orderId, String userEmail) {
		if (orderId == null || orderId.isBlank()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "orderId is required");
		}
		PaymentOrder order = paymentOrderRepository.findByOrderIdForUpdate(orderId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Payment order not found"));
		if (!order.getUserEmail().equalsIgnoreCase(userEmail)) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "This payment order belongs to another account");
		}
		if (order.getStatus() == PaymentOrderStatus.PAID) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "A paid order cannot be cancelled");
		}
		order.setStatus(PaymentOrderStatus.CANCELLED);
		paymentOrderRepository.save(order);
	}

	private void validateRequest(CreatePaymentRequest request, String userEmail) {
		if (request == null || request.roomId() == null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "roomId is required");
		}
		if (isBlank(request.guestName()) || isBlank(request.guestEmail()) || isBlank(request.guestPhone())) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Guest name, email, and phone are required");
		}
		if (!request.guestEmail().trim().equalsIgnoreCase(userEmail)) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Booking email must match the signed-in account");
		}
		if (request.checkInDate() == null || request.checkOutDate() == null
				|| request.checkInDate().isBefore(LocalDate.now())
				|| !request.checkOutDate().isAfter(request.checkInDate())) {
			throw new ResponseStatusException(
					HttpStatus.BAD_REQUEST, "Choose valid future check-in and check-out dates");
		}
	}

	private String createRazorpayOrder(BigDecimal totalAmount) {
		long amountPaise = toPaise(totalAmount);
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("amount", amountPaise);
		body.put("currency", "INR");
		body.put("receipt", "hotel_" + UUID.randomUUID().toString().replace("-", ""));
		try {
			Map<String, Object> result = razorpayClient.post()
					.uri("/orders")
					.body(body)
					.retrieve()
					.body(new ParameterizedTypeReference<>() {
					});
			Object responseId = result == null ? null : result.get("id");
			String orderId = responseId instanceof String id ? id : null;
			if (orderId == null || orderId.isBlank()) {
				throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Razorpay did not return an order ID");
			}
			return orderId;
		} catch (RestClientException exception) {
			throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Unable to create a Razorpay order", exception);
		}
	}

	private String verifySimulatedPayment(VerifyPaymentRequest request) {
		if (!Boolean.TRUE.equals(request.simulated())) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Use the test payment flow for test_mode orders");
		}
		return "pay_test_" + UUID.randomUUID().toString().replace("-", "");
	}

	private String verifyRazorpayPayment(VerifyPaymentRequest request, PaymentOrder order) {
		if (Boolean.TRUE.equals(request.simulated())
				|| isBlank(request.paymentId())
				|| isBlank(request.signature())) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Razorpay payment ID and signature are required");
		}
		String expectedSignature = calculateSignature(request.orderId(), request.paymentId());
		boolean signatureMatches = MessageDigest.isEqual(
				expectedSignature.getBytes(StandardCharsets.US_ASCII),
				request.signature().toLowerCase(Locale.ROOT).getBytes(StandardCharsets.US_ASCII));
		if (!signatureMatches) {
			throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Razorpay payment signature is invalid");
		}

		try {
			Map<String, Object> payment = razorpayClient.get()
					.uri("/payments/{paymentId}", request.paymentId())
					.retrieve()
					.body(new ParameterizedTypeReference<>() {
					});
			if (payment == null
					|| !request.orderId().equals(payment.get("order_id"))
					|| !"captured".equals(payment.get("status"))
					|| !(payment.get("amount") instanceof Number amount)
					|| amount.longValue() != toPaise(order.getTotalAmount())) {
				throw new ResponseStatusException(HttpStatus.PAYMENT_REQUIRED, "Razorpay payment is not captured");
			}
		} catch (RestClientException exception) {
			throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Unable to verify Razorpay payment", exception);
		}
		return request.paymentId();
	}

	private String calculateSignature(String orderId, String paymentId) {
		try {
			Mac mac = Mac.getInstance("HmacSHA256");
			mac.init(new SecretKeySpec(keySecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
			byte[] digest = mac.doFinal((orderId + "|" + paymentId).getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(digest);
		} catch (GeneralSecurityException exception) {
			throw new IllegalStateException("Unable to verify Razorpay signature", exception);
		}
	}

	private PaymentVerificationResponse toVerificationResponse(PaymentOrder order) {
		return new PaymentVerificationResponse(
				order.getBooking().getId(),
				order.getPaymentId(),
				order.getBooking().getPaymentStatus().name());
	}

	private long toPaise(BigDecimal amount) {
		return amount.movePointRight(2).setScale(0, RoundingMode.UNNECESSARY).longValueExact();
	}

	private boolean isBlank(String value) {
		return value == null || value.isBlank();
	}

	public record CreatePaymentRequest(
			Long roomId,
			String guestName,
			String guestEmail,
			String guestPhone,
			LocalDate checkInDate,
			LocalDate checkOutDate) {
	}

	public record VerifyPaymentRequest(
			String orderId,
			String paymentId,
			String signature,
			Boolean simulated) {
	}

	public record CheckoutOrderResponse(
			String orderId,
			String keyId,
			long amount,
			BigDecimal subtotal,
			BigDecimal gstAmount,
			BigDecimal totalAmount,
			BigDecimal gstRatePercent,
			String currency,
			boolean testMode) {
	}

	public record PaymentVerificationResponse(Long bookingId, String paymentId, String paymentStatus) {
	}
}
