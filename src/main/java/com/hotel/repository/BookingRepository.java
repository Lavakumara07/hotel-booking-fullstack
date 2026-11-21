package com.hotel.repository;

import com.hotel.model.Booking;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface BookingRepository extends JpaRepository<Booking, Long> {

	List<Booking> findByGuestEmailIgnoreCase(String guestEmail);

	boolean existsByRoom_Id(Long roomId);

	@Query("""
			SELECT CASE WHEN COUNT(booking) > 0 THEN true ELSE false END
			FROM Booking booking
			WHERE booking.room.id = :roomId
				AND LOWER(COALESCE(booking.status, '')) <> 'cancelled'
				AND booking.checkInDate < :checkOutDate
				AND booking.checkOutDate > :checkInDate
			""")
	boolean existsOverlappingBooking(
			@Param("roomId") Long roomId,
			@Param("checkInDate") LocalDate checkInDate,
			@Param("checkOutDate") LocalDate checkOutDate);
}
