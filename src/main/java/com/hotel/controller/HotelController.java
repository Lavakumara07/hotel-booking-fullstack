package com.hotel.controller;

import com.hotel.model.Booking;
import com.hotel.model.Room;
import com.hotel.service.HotelService;
import com.hotel.service.HotelService.CreateRoomRequest;
import com.hotel.service.HotelService.UpdateRoomPriceRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api")
public class HotelController {

	private final HotelService hotelService;

	public HotelController(HotelService hotelService) {
		this.hotelService = hotelService;
	}

	@GetMapping("/rooms")
	public List<Room> getAllRooms() {
		return hotelService.getAllRooms();
	}

	@PostMapping("/rooms")
	@ResponseStatus(HttpStatus.CREATED)
	public Room createRoom(@RequestBody CreateRoomRequest request) {
		return hotelService.createRoom(request);
	}

	@PatchMapping("/rooms/{roomId}/price")
	public Room updateRoomPrice(
			@PathVariable Long roomId,
			@RequestBody UpdateRoomPriceRequest request) {
		return hotelService.updateRoomPrice(roomId, request);
	}

	@DeleteMapping("/rooms/{roomId}")
	public ResponseEntity<Void> deleteRoom(@PathVariable Long roomId) {
		hotelService.deleteRoom(roomId);
		return ResponseEntity.noContent().build();
	}

	@GetMapping("/rooms/available")
	public List<Room> getAvailableRooms(
			@RequestParam LocalDate checkInDate,
			@RequestParam LocalDate checkOutDate) {
		return hotelService.getAvailableRooms(checkInDate, checkOutDate);
	}

	@GetMapping("/bookings")
	public List<Booking> getAllBookings() {
		return hotelService.getAllBookings();
	}

	@GetMapping("/bookings/mine")
	public List<Booking> getMyBookings(
			@RequestParam(required = false) String guestEmail,
			Authentication authentication) {
		String signedInEmail = authentication.getName();
		if (guestEmail != null && !guestEmail.isBlank()
				&& !guestEmail.equalsIgnoreCase(signedInEmail)) {
			throw new ResponseStatusException(
					HttpStatus.FORBIDDEN, "You can only view bookings for your signed-in account");
		}
		return hotelService.getBookingsByGuestEmail(signedInEmail);
	}

	@PatchMapping("/bookings/{bookingId}/cancel")
	public Booking cancelBooking(@PathVariable Long bookingId, Authentication authentication) {
		boolean isAdmin = authentication.getAuthorities().stream()
				.anyMatch(authority -> authority.getAuthority().equals("ROLE_ADMIN"));
		return hotelService.cancelBooking(bookingId, authentication.getName(), isAdmin);
	}
}
