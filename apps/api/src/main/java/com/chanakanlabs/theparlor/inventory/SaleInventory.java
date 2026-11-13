package com.chanakanlabs.theparlor.inventory;

import java.util.Collection;
import java.util.List;
import java.util.Map;
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

  /**
   * What each edition would cost and how many units could be bought at the branch right now. A
   * quote reserves nothing; hold() revalidates atomically. Unknown editions are absent.
   */
  Map<UUID, Quote> quote(UUID branchId, Collection<UUID> editionIds);

  record PricedItem(
      UUID editionId,
      UUID gameId,
      String gameName,
      String editionLanguage,
      int quantity,
      long unitPriceMinor) {}

  /** Why an edition cannot be bought at a branch; the first applicable reason wins. */
  enum Blocker {
    GAME_INACTIVE,
    EDITION_INACTIVE,
    NOT_SOLD_AT_LOCATION
  }

  /**
   * blocker is null when the edition is on sale. unitPriceMinor is zero and available is zero when
   * the branch has never priced the edition.
   */
  record Quote(
      UUID editionId,
      UUID gameId,
      String gameName,
      String editionKind,
      String editionLanguage,
      long unitPriceMinor,
      int available,
      Blocker blocker) {}
}
