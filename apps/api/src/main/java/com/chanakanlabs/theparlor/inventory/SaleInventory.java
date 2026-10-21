package com.chanakanlabs.theparlor.inventory;

import java.util.List;
import java.util.UUID;

/**
 * Module interface for sale commitments. Call inside the order/cart transaction so stock changes
 * roll back with the order. Implementations must not expose inventory entities or repositories.
 */
public interface SaleInventory {
  /** Hold all quantities or none and return current server-side price/name snapshots. */
  List<PricedItem> hold(UUID branchId, List<Selection> selections);

  /** Release an order's held quantities when its owning workflow cancels it. */
  void release(UUID branchId, List<Selection> selections);

  /** Decrement both on-hand and held quantities when the owning workflow fulfills an order. */
  void fulfill(UUID branchId, List<Selection> selections);

  record Selection(UUID editionId, int quantity) {}

  record PricedItem(UUID editionId, String gameName, int quantity, long unitPriceMinor) {}
}
