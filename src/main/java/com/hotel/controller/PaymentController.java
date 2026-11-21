package com.hotel.controller;

import com.hotel.service.PaymentService;
import com.hotel.service.PaymentService.CheckoutOrderResponse;
import com.hotel.service.PaymentService.CreatePaymentRequest;
import com.hotel.service.PaymentService.PaymentVerificationResponse;
import com.hotel.service.PaymentService.VerifyPaymentRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/payment")
public class PaymentController {

	private final PaymentService paymentService;

	public PaymentController(PaymentService paymentService) {
		this.paymentService = paymentService;
	}

	@PostMapping("/create-order")
	public CheckoutOrderResponse createOrder(
			@RequestBody CreatePaymentRequest request,
			Authentication authentication) {
		return paymentService.createOrder(request, authentication.getName());
	}

	@PostMapping("/verify")
	public PaymentVerificationResponse verifyPayment(
			@RequestBody VerifyPaymentRequest request,
			Authentication authentication) {
		return paymentService.verifyPayment(request, authentication.getName());
	}

	@PostMapping("/cancel-order")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void cancelOrder(@RequestParam String orderId, Authentication authentication) {
		paymentService.cancelOrder(orderId, authentication.getName());
	}
}
