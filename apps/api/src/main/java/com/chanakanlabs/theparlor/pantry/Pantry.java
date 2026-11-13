package com.chanakanlabs.theparlor.pantry;

import java.util.List;
import java.util.UUID;

/**
 * Module interface for pantry commitments. Call inside the owning workflow's transaction so stock
 * changes roll back with it. Every method takes all quantities or none and answers 409 when the
 * branch cannot serve them.
 */
public interface Pantry {
  /** Holds every quantity at the branch and returns the current price and name snapshots. */
  List<PricedItem> hold(UUID branchId, List<Selection> selections);

  /** Returns held units when the owning workflow is cancelled. */
  void release(UUID branchId, List<Selection> selections);

  /** Consumes held units, decrementing both on-hand and held, when the food is served. */
  void fulfill(UUID branchId, List<Selection> selections);

  record Selection(UUID menuItemId, int quantity) {}

  /** name is the en-US menu name. */
  record PricedItem(UUID menuItemId, String name, int quantity, long unitPriceMinor) {}
}
