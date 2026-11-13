package com.chanakanlabs.theparlor.payments.internal.persistence;

import com.chanakanlabs.theparlor.payments.internal.domain.Payment;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {
  Optional<Payment> findByOrderId(UUID orderId);

  List<Payment> findByOrderIdIn(Collection<UUID> orderIds);

  Optional<Payment> findByReservationId(UUID reservationId);

  List<Payment> findByReservationIdIn(Collection<UUID> reservationIds);
}
