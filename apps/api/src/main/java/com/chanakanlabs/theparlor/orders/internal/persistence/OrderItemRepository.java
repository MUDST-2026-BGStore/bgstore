package com.chanakanlabs.theparlor.orders.internal.persistence;

import com.chanakanlabs.theparlor.orders.internal.domain.OrderItem;
import com.chanakanlabs.theparlor.orders.internal.domain.OrderItemId;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderItemRepository extends JpaRepository<OrderItem, OrderItemId> {
  List<OrderItem> findByIdOrderIdIn(Collection<UUID> orderIds);
}
