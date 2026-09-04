package com.chanakanlabs.bgstore.inventory;

import java.util.List;

/**
 * A page of inventory rows together with the totals for the whole filtered set.
 *
 * @param totalElements games matching the filter, before pagination
 * @param totalAvailable copies free across those games
 * @param totalInUse copies out on a session across those games
 */
record GamePage(
    List<GameSummaryRow> rows, long totalElements, long totalAvailable, long totalInUse) {

  /** No games matched, which the stat tiles still have to render as zeros. */
  static final GamePage EMPTY = new GamePage(List.of(), 0L, 0L, 0L);
}
