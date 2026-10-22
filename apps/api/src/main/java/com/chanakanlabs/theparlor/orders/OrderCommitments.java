package com.chanakanlabs.theparlor.orders;

import com.chanakanlabs.theparlor.orders.internal.persistence.OrderRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read-only module interface for catalog retirement checks. */
@Service
@Transactional(readOnly = true)
public class OrderCommitments {
  private final OrderRepository repository;

  public OrderCommitments(OrderRepository repository) {
    this.repository = repository;
  }

  public boolean hasPlacedOrder(java.util.Set<UUID> editionIds) {
    return !editionIds.isEmpty() && repository.hasPlacedEdition(editionIds);
  }
}
