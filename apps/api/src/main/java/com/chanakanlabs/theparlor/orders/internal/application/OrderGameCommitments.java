package com.chanakanlabs.theparlor.orders.internal.application;

import com.chanakanlabs.theparlor.catalog.spi.GameCommitments;
import com.chanakanlabs.theparlor.orders.internal.persistence.OrderRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Tells catalog whether a game has a placed retail order. */
@Service
@Transactional(readOnly = true)
class OrderGameCommitments implements GameCommitments {
  private final OrderRepository orders;

  OrderGameCommitments(OrderRepository orders) {
    this.orders = orders;
  }

  @Override
  public boolean hasOpenCommitments(UUID gameId) {
    return orders.hasPlacedGame(gameId);
  }
}
