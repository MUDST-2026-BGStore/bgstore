package com.chanakanlabs.theparlor.customers.internal.persistence;

import com.chanakanlabs.theparlor.customers.internal.domain.Customer;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface CustomerRepository extends JpaRepository<Customer, UUID> {
  @Modifying
  @Query(
      value =
          "INSERT INTO customers (account_id) VALUES (:accountId)"
              + " ON CONFLICT (account_id) DO NOTHING",
      nativeQuery = true)
  void insertIfAbsent(UUID accountId);

  @Query("select c.id from Customer c where c.accountId = :accountId")
  UUID idOfAccount(UUID accountId);
}
