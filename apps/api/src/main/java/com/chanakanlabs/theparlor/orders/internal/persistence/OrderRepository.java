package com.chanakanlabs.theparlor.orders.internal.persistence;

import com.chanakanlabs.theparlor.orders.internal.domain.Order;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderRepository
    extends JpaRepository<Order, UUID>, JpaSpecificationExecutor<Order> {
  Optional<Order> findByIdAndBranchId(UUID id, UUID branchId);

  /** Serializes cancellation and fulfillment of one order. */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select o from Order o where o.id = :id and o.branchId = :branchId")
  Optional<Order> lockByIdAndBranchId(UUID id, UUID branchId);

  @Query(
      "select (count(o) > 0) from Order o, OrderItem i where i.id.orderId = o.id and i.id.editionId in :ids and o.status = com.chanakanlabs.theparlor.orders.internal.domain.OrderStatus.PLACED")
  boolean hasPlacedEdition(@Param("ids") Set<UUID> ids);
}
