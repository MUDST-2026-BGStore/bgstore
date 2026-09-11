package com.chanakanlabs.bgstore.inventory;

import com.chanakanlabs.bgstore.contract.model.GameAvailability;
import com.chanakanlabs.bgstore.contract.model.GameLifecycle;

/**
 * Derives the status the inventory screens show.
 *
 * <p>A retired game reports {@code retired} whatever its shelves hold; for everything else the
 * status follows the copies, where availability is copies minus the copies currently out on a
 * session.
 */
final class GameAvailabilities {

  private GameAvailabilities() {}

  static GameAvailability of(GameLifecycle lifecycle, int copies, int available) {
    if (lifecycle == GameLifecycle.RETIRED) {
      return GameAvailability.RETIRED;
    }
    if (copies == 0) {
      return GameAvailability.NOT_STOCKED;
    }
    return available == 0 ? GameAvailability.ALL_COPIES_OUT : GameAvailability.AVAILABLE;
  }
}
