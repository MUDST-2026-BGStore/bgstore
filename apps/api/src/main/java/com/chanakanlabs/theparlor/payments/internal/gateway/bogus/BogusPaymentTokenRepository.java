package com.chanakanlabs.theparlor.payments.internal.gateway.bogus;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

interface BogusPaymentTokenRepository extends JpaRepository<BogusPaymentToken, byte[]> {
  /** Locked so two concurrent authorizations cannot both spend a single-use token. */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select t from BogusPaymentToken t where t.tokenHash = :hash")
  Optional<BogusPaymentToken> lockByHash(byte[] hash);
}
