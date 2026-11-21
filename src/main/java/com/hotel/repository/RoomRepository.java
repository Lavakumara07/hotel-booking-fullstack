package com.hotel.repository;

import com.hotel.model.Room;
import com.hotel.model.PaymentOrderStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface RoomRepository extends JpaRepository<Room, Long> {

	boolean existsByRoomNumberIgnoreCase(String roomNumber);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("SELECT room FROM Room room WHERE room.id = :roomId")
	Optional<Room> findByIdForUpdate(@Param("roomId") Long roomId);

	@Query("""
			SELECT room
			FROM Room room
			WHERE room.available = true
				AND NOT EXISTS (
					SELECT booking.id
					FROM Booking booking
					WHERE booking.room = room
						AND LOWER(COALESCE(booking.status, '')) <> 'cancelled'
						AND booking.checkInDate < :checkOutDate
						AND booking.checkOutDate > :checkInDate
				)
				AND NOT EXISTS (
					SELECT paymentOrder.id
					FROM PaymentOrder paymentOrder
					WHERE paymentOrder.room = room
						AND paymentOrder.status = :pendingStatus
						AND paymentOrder.checkInDate < :checkOutDate
						AND paymentOrder.checkOutDate > :checkInDate
				)
			""")
	List<Room> findAvailableBetween(
			@Param("checkInDate") LocalDate checkInDate,
			@Param("checkOutDate") LocalDate checkOutDate,
			@Param("pendingStatus") PaymentOrderStatus pendingStatus);
}
