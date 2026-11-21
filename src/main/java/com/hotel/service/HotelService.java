package com.hotel.service;

import com.hotel.model.Booking;
import com.hotel.model.PaymentOrderStatus;
import com.hotel.model.Room;
import com.hotel.repository.BookingRepository;
import com.hotel.repository.PaymentOrderRepository;
import com.hotel.repository.RoomRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Service
public class HotelService {

	private static final String CANCELLED_STATUS = "CANCELLED";

	private final RoomRepository roomRepository;
	private final BookingRepository bookingRepository;
	private final PaymentOrderRepository paymentOrderRepository;

	public HotelService(
			RoomRepository roomRepository,
			BookingRepository bookingRepository,
			PaymentOrderRepository paymentOrderRepository) {
		this.roomRepository = roomRepository;
		this.bookingRepository = bookingRepository;
		this.paymentOrderRepository = paymentOrderRepository;
	}

	@Transactional(readOnly = true)
	public List<Room> getAllRooms() {
		return roomRepository.findAll();
	}

	@Transactional
	public Room createRoom(CreateRoomRequest request) {
		if (request == null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Room details are required");
		}
		if (isBlank(request.roomNumber()) || isBlank(request.roomType())) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Room number and type are required");
		}
		validatePrice(request.pricePerNight());

		String roomNumber = request.roomNumber().trim();
		if (roomRepository.existsByRoomNumberIgnoreCase(roomNumber)) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "Room number already exists");
		}

		Room room = new Room();
		room.setRoomNumber(roomNumber);
		room.setRoomType(request.roomType().trim());
		room.setPricePerNight(request.pricePerNight());
		room.setDescription(request.description());
		room.setImageUrl(request.imageUrl());
		room.setAvailable(true);
		return roomRepository.save(room);
	}

	@Transactional
	public Room updateRoomPrice(Long roomId, UpdateRoomPriceRequest request) {
		if (request == null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Price is required");
		}
		validatePrice(request.pricePerNight());
		Room room = roomRepository.findById(roomId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Room not found"));
		room.setPricePerNight(request.pricePerNight());
		return roomRepository.save(room);
	}

	@Transactional
	public void deleteRoom(Long roomId) {
		Room room = roomRepository.findById(roomId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Room not found"));
		if (bookingRepository.existsByRoom_Id(roomId)
				|| paymentOrderRepository.existsByRoom_Id(roomId)) {
			throw new ResponseStatusException(
					HttpStatus.CONFLICT, "Room cannot be deleted because it has booking or payment history");
		}
		roomRepository.delete(room);
	}

	@Transactional(readOnly = true)
	public List<Room> getAvailableRooms(LocalDate checkInDate, LocalDate checkOutDate) {
		validateDates(checkInDate, checkOutDate);
		return roomRepository.findAvailableBetween(
				checkInDate, checkOutDate, PaymentOrderStatus.PENDING);
	}

	@Transactional(readOnly = true)
	public List<Booking> getAllBookings() {
		return bookingRepository.findAll();
	}

	@Transactional(readOnly = true)
	public List<Booking> getBookingsByGuestEmail(String guestEmail) {
		if (isBlank(guestEmail)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "guestEmail is required");
		}
		return bookingRepository.findByGuestEmailIgnoreCase(guestEmail.trim());
	}

	@Transactional
	public Booking cancelBooking(Long bookingId, String requesterEmail, boolean isAdmin) {
		Booking booking = bookingRepository.findById(bookingId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Booking not found"));
		if (!isAdmin && !booking.getGuestEmail().equalsIgnoreCase(requesterEmail)) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You can only cancel your own booking");
		}
		booking.setStatus(CANCELLED_STATUS);
		return bookingRepository.save(booking);
	}

	private void validateDates(LocalDate checkInDate, LocalDate checkOutDate) {
		if (checkInDate == null || checkOutDate == null || !checkOutDate.isAfter(checkInDate)) {
			throw new ResponseStatusException(
					HttpStatus.BAD_REQUEST, "checkOutDate must be after checkInDate");
		}
	}

	private boolean isBlank(String value) {
		return value == null || value.isBlank();
	}

	private void validatePrice(BigDecimal pricePerNight) {
		if (pricePerNight == null || pricePerNight.signum() <= 0) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Price per night must be greater than zero");
		}
	}

	public record CreateRoomRequest(
			String roomNumber,
			String roomType,
			BigDecimal pricePerNight,
			String description,
			String imageUrl) {
	}

	public record UpdateRoomPriceRequest(BigDecimal pricePerNight) {
	}

}
