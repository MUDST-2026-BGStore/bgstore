package com.chanakanlabs.theparlor.payments.internal.persistence;

import com.chanakanlabs.theparlor.payments.internal.domain.Payment;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {
  List<Payment> findByOrderIdIn(Collection<UUID> orderIds);

  List<Payment> findByReservationIdIn(Collection<UUID> reservationIds);

  /** Locked so a provider notice cannot settle a payment while its payable changes it. */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select p from Payment p where p.orderId = :orderId")
  Optional<Payment> lockByOrderId(UUID orderId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select p from Payment p where p.reservationId = :reservationId")
  Optional<Payment> lockByReservationId(UUID reservationId);

  /** Locked so a provider notice and the expiry job cannot both settle the same payment. */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select p from Payment p where p.gateway = :gateway and p.gatewayReference = :reference")
  Optional<Payment> lockByGatewayReference(String gateway, String reference);

  @Query(
      "select p.orderId from Payment p where p.orderId is not null"
          + " and p.method = com.chanakanlabs.theparlor.payments.Payments.Method.PROMPTPAY"
          + " and p.status = com.chanakanlabs.theparlor.payments.Payments.Status.PENDING"
          + " and p.expiresAt <= :now order by p.expiresAt")
  List<UUID> expiredUnpaidOrders(Instant now);

  @Query(
      "select p.reservationId from Payment p where p.reservationId is not null"
          + " and p.method = com.chanakanlabs.theparlor.payments.Payments.Method.PROMPTPAY"
          + " and p.status = com.chanakanlabs.theparlor.payments.Payments.Status.PENDING"
          + " and p.expiresAt <= :now order by p.expiresAt")
  List<UUID> expiredUnpaidReservations(Instant now);
}
