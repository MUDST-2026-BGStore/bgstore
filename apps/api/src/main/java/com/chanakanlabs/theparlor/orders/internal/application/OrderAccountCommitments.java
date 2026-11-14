package com.chanakanlabs.theparlor.orders.internal.application;

import com.chanakanlabs.theparlor.account.spi.AccountCommitments;
import com.chanakanlabs.theparlor.orders.internal.persistence.OrderRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class OrderAccountCommitments implements AccountCommitments {
  private final OrderRepository orders;

  public OrderAccountCommitments(OrderRepository orders) {
    this.orders = orders;
  }

  @Override
  @Transactional(readOnly = true)
  public boolean hasOpenCommitments(String subject) {
    return orders.hasPlacedForCustomer(subject);
  }
}
