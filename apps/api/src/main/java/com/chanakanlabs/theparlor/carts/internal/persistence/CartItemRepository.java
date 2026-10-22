package com.chanakanlabs.theparlor.carts.internal.persistence;

import com.chanakanlabs.theparlor.carts.internal.domain.CartItem;
import com.chanakanlabs.theparlor.carts.internal.domain.CartItemId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CartItemRepository extends JpaRepository<CartItem, CartItemId> {}
