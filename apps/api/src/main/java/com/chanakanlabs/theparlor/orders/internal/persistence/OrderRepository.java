package com.chanakanlabs.theparlor.orders.internal.persistence;

import com.chanakanlabs.theparlor.orders.internal.domain.Order;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderRepository extends JpaRepository<Order, UUID> {

  @Query(
      "select (count(o) > 0) from Order o, OrderItem i where i.id.orderId = o.id and i.id.editionId in :ids and o.status = com.chanakanlabs.theparlor.orders.internal.domain.OrderStatus.PLACED")
  boolean hasPlacedEdition(@Param("ids") Set<UUID> ids);
}
