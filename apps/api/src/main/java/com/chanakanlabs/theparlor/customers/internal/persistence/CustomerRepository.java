package com.chanakanlabs.theparlor.customers.internal.persistence;

import com.chanakanlabs.theparlor.customers.internal.domain.Customer;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomerRepository extends JpaRepository<Customer, UUID> {}
