package com.hotel.repository;

import com.hotel.model.PaymentOrder;
import com.hotel.model.PaymentOrderStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface PaymentOrderRepository extends JpaRepository<PaymentOrder, Long> {

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("SELECT paymentOrder FROM PaymentOrder paymentOrder WHERE paymentOrder.orderId = :orderId")
	Optional<PaymentOrder> findByOrderIdForUpdate(@Param("orderId") String orderId);

	boolean existsByRoom_IdAndStatus(Long roomId, PaymentOrderStatus status);

	boolean existsByRoom_Id(Long roomId);
}
