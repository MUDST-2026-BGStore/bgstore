package com.chanakanlabs.theparlor.inventory.internal.application;

import com.chanakanlabs.theparlor.inventory.SaleInventory;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Unfinished stock operations shared by direct orders and cart checkout. */
@Service
@Transactional(propagation = Propagation.MANDATORY)
public class SaleInventoryService implements SaleInventory {
  // TODO(sale-integration): order/cart services must authorize the caller, validate the lifecycle
  // transition and enforce Idempotency-Key in the SAME transaction before calling this interface.
  // Release/fulfill take selections from persisted order items, never from client-supplied
  // quantities.
  // Add the inventory module to consumers' allowedDependencies when wiring those services.
  // TODO(sale-locking): lock branch/edition rows in a stable order for every operation, including
  // manual adjustments. Coordinate offering/price changes with the same transaction strategy.
  // Database checks enforce 0<=held<=onHand but do not replace concurrency control.

  @Override
  public List<PricedItem> hold(UUID branchId, List<Selection> selections) {
    // TODO(sale-hold): reject empty/duplicate selections and quantities outside contract limits.
    // Validate active branch/game/edition and saleEnabled using public module interfaces; load
    // and lock every stock row, check onHand-held>=quantity, then increment held through domain
    // methods. Fail the entire operation with 409 if any line lacks stock. Return locked prices
    // and game names for order snapshots; use exact arithmetic. Never consume rental copies.
    throw new UnsupportedOperationException(
        "Transactional sale stock holds are not implemented yet");
  }

  @Override
  public void release(UUID branchId, List<Selection> selections) {
    // TODO(sale-release): lock rows and decrement held only through domain methods; check held
    // never becomes negative. Do not require current offering flags for existing commitments.
    // The owning order transaction must prevent repeated cancellation from releasing stock twice.
    throw new UnsupportedOperationException("Sale stock hold release is not implemented yet");
  }

  @Override
  public void fulfill(UUID branchId, List<Selection> selections) {
    // TODO(sale-fulfill): lock rows, verify held/onHand cover all quantities and decrement both
    // through domain methods. Honor existing commitments even if an offering is now disabled.
    // The owning order transaction must prevent repeated fulfillment from decrementing twice.
    throw new UnsupportedOperationException("Sale stock fulfillment is not implemented yet");
  }
}
