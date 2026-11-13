package com.chanakanlabs.theparlor.carts.internal.persistence;

import com.chanakanlabs.theparlor.carts.internal.domain.Cart;
import com.chanakanlabs.theparlor.carts.internal.domain.CartStatus;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface CartRepository extends JpaRepository<Cart, UUID> {
  /** Serializes every change to one cart, including checkout. */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select c from Cart c where c.id = :id")
  Optional<Cart> lockById(UUID id);

  Optional<Cart> findByAccountIdAndStatus(UUID accountId, CartStatus status);

  /** Opens the account's cart unless it already has an open one; safe under concurrency. */
  @Modifying
  @Query(
      value =
          "INSERT INTO carts (account_id, token_hash, status, fulfillment_method)"
              + " VALUES (:accountId, :tokenHash, 'OPEN', 'SHIPPING')"
              + " ON CONFLICT (account_id) WHERE account_id IS NOT NULL AND status = 'OPEN'"
              + " DO NOTHING",
      nativeQuery = true)
  void openForAccountIfAbsent(UUID accountId, byte[] tokenHash);
}
