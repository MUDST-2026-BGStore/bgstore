package com.chanakanlabs.theparlor.customers.internal.application;

import com.chanakanlabs.theparlor.customers.Customers;
import com.chanakanlabs.theparlor.customers.internal.persistence.CustomerRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(propagation = Propagation.MANDATORY)
class CustomersService implements Customers {
  private final CustomerRepository customers;

  CustomersService(CustomerRepository customers) {
    this.customers = customers;
  }

  @Override
  public UUID forAccount(UUID accountId) {
    // Insert-or-ignore lets two first orders race without failing either transaction.
    customers.insertIfAbsent(accountId);
    return customers.idOfAccount(accountId);
  }
}
