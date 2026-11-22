package com.hotel.hotel_booking_system;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.util.JsonPathExpectationsHelper;

import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
		"app.jwt.secret=test-only-signing-key-with-at-least-32-bytes",
		"spring.datasource.url=jdbc:h2:mem:hotel-booking-test;MODE=MySQL;DB_CLOSE_DELAY=-1",
		"spring.datasource.username=sa",
		"spring.datasource.password=",
		"spring.datasource.driver-class-name=org.h2.Driver",
		"spring.jpa.hibernate.ddl-auto=create-drop",
		"razorpay.key-id=test_mode",
		"razorpay.key-secret=test_mode"
})
class HotelBookingSystemApplicationTests {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void publicRoomsAndAdminOnlyRoomManagement() throws Exception {
		mockMvc.perform(get("/api/rooms"))
				.andExpect(status().isOk());

		mockMvc.perform(post("/api/rooms")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"roomNumber":"UNAUTH","roomType":"Suite","pricePerNight":10000}
								"""))
				.andExpect(status().isUnauthorized());

		String adminToken = login("admin@hotel.com", "admin123");
		MvcResult created = mockMvc.perform(post("/api/rooms")
						.header("Authorization", "Bearer " + adminToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"roomNumber":"ADMIN-TEST","roomType":"Admin Test Suite","pricePerNight":10000,
								"description":"Temporary test room","imageUrl":"https://placehold.co/800x600"}
								"""))
				.andExpect(status().isCreated())
				.andReturn();
		Long roomId = jsonNumber(created, "$.id");

		mockMvc.perform(patch("/api/rooms/{roomId}/price", roomId)
						.header("Authorization", "Bearer " + adminToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"pricePerNight\":12500}"))
				.andExpect(status().isOk());

		mockMvc.perform(delete("/api/rooms/{roomId}", roomId)
						.header("Authorization", "Bearer " + adminToken))
				.andExpect(status().isNoContent());
	}

	@Test
	void simulatedPaymentConfirmsBookingAndRestrictsAllBookingsToAdmin() throws Exception {
		String email = "guest-" + UUID.randomUUID() + "@example.com";
		String customerToken = register(email);
		MvcResult roomResponse = mockMvc.perform(get("/api/rooms"))
				.andExpect(status().isOk())
				.andReturn();
		Long roomId = jsonNumber(roomResponse, "$[0].id");
		LocalDate checkIn = LocalDate.now().plusDays(30);
		LocalDate checkOut = checkIn.plusDays(2);

		String checkoutBody = String.format(
				"{\"roomId\":%d,\"guestName\":\"Test Guest\",\"guestEmail\":\"%s\","
						+ "\"guestPhone\":\"555-0100\",\"checkInDate\":\"%s\",\"checkOutDate\":\"%s\"}",
				roomId, email, checkIn, checkOut);
		MvcResult order = mockMvc.perform(post("/api/payment/create-order")
						.header("Authorization", "Bearer " + customerToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content(checkoutBody))
				.andExpect(status().isOk())
				.andReturn();
		String orderId = jsonString(order, "$.orderId");
		assertEquals(true, jsonBoolean(order, "$.testMode"));
		assertNotNull(jsonString(order, "$.gstAmount"));

		MvcResult payment = mockMvc.perform(post("/api/payment/verify")
						.header("Authorization", "Bearer " + customerToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content(String.format("{\"orderId\":\"%s\",\"simulated\":true}", orderId)))
				.andExpect(status().isOk())
				.andReturn();
		assertEquals("PAID", jsonString(payment, "$.paymentStatus"));
		assertNotNull(jsonString(payment, "$.paymentId"));

		mockMvc.perform(get("/api/bookings")
						.header("Authorization", "Bearer " + customerToken))
				.andExpect(status().isForbidden());

		mockMvc.perform(get("/api/bookings/mine")
						.param("guestEmail", email)
						.header("Authorization", "Bearer " + customerToken))
				.andExpect(status().isOk())
				.andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
						.jsonPath("$[0].paymentStatus").value("PAID"));
	}

	private String login(String email, String password) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content(String.format("{\"email\":\"%s\",\"password\":\"%s\"}", email, password)))
				.andExpect(status().isOk())
				.andReturn();
		return jsonString(result, "$.token");
	}
	private String register(String email) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/auth/register")
						.contentType(MediaType.APPLICATION_JSON)
						.content(String.format(
								"{\"fullName\":\"Test Guest\",\"email\":\"%s\",\"password\":\"test-password-123\"}",
								email)))
				.andExpect(status().isCreated())
				.andReturn();
		return jsonString(result, "$.token");
	}

	private String jsonString(MvcResult result, String expression) throws Exception {
		return new JsonPathExpectationsHelper(expression)
				.evaluateJsonPath(result.getResponse().getContentAsString(), String.class);
	}

	private Long jsonNumber(MvcResult result, String expression) throws Exception {
		Number value = new JsonPathExpectationsHelper(expression)
				.evaluateJsonPath(result.getResponse().getContentAsString(), Number.class);
		return value.longValue();
	}

	private boolean jsonBoolean(MvcResult result, String expression) throws Exception {
		Boolean value = new JsonPathExpectationsHelper(expression)
				.evaluateJsonPath(result.getResponse().getContentAsString(), Boolean.class);
		return value;
	}
}
