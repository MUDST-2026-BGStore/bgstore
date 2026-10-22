package com.chanakanlabs.theparlor.orders.internal.persistence;

import com.chanakanlabs.theparlor.orders.internal.domain.OrderItem;
import com.chanakanlabs.theparlor.orders.internal.domain.OrderItemId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderItemRepository extends JpaRepository<OrderItem, OrderItemId> {}
