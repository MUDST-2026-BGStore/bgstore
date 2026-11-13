package com.chanakanlabs.theparlor.inventory.internal.application;

import com.chanakanlabs.theparlor.contracts.model.BranchEditionStock;
import com.chanakanlabs.theparlor.contracts.model.GameEdition;
import com.chanakanlabs.theparlor.contracts.model.Money;
import com.chanakanlabs.theparlor.contracts.model.RentalCopy;
import com.chanakanlabs.theparlor.contracts.model.RentalCopyOperationalStatus;
import com.chanakanlabs.theparlor.contracts.model.RentalCopyState;
import com.chanakanlabs.theparlor.inventory.internal.domain.BranchEdition;
import com.chanakanlabs.theparlor.inventory.spi.CopyCommitments.Use;

/**
 * Entity-to-contract mapping. Amounts are integer THB satang; zero is a valid price, never a
 * missing-price sentinel.
 */
final class InventoryMapper {
  private InventoryMapper() {}

  /** sellable says whether the branch, offering, game and edition all currently allow sales. */
  static BranchEditionStock stock(BranchEdition row, GameEdition edition, boolean sellable) {
    return new BranchEditionStock(
        edition.getId(),
        BranchEditionStock.EditionKindEnum.fromValue(edition.getEditionKind().getValue()),
        edition.getLanguageCode(),
        edition.getActive(),
        new Money(row.getSalePriceMinor()),
        row.getSaleOnHand(),
        row.getSaleHeld(),
        saleAvailable(row, sellable));
  }

  static int saleAvailable(BranchEdition row, boolean sellable) {
    return sellable ? Math.max(0, row.getSaleOnHand() - row.getSaleHeld()) : 0;
  }

  /** use is how the copy is committed right now, or null when it is free. */
  static RentalCopy copy(
      com.chanakanlabs.theparlor.inventory.internal.domain.RentalCopy copy,
      Use use,
      String latestReason) {
    var result =
        new RentalCopy(
            copy.getId(),
            copy.getBranchId(),
            copy.getGameId(),
            copy.getAssetCode(),
            RentalCopyOperationalStatus.valueOf(copy.getOperationalStatus().name()),
            state(copy, use));
    result.setReason(latestReason);
    return result;
  }

  private static RentalCopyState state(
      com.chanakanlabs.theparlor.inventory.internal.domain.RentalCopy copy, Use use) {
    return switch (copy.getOperationalStatus()) {
      case RETIRED -> RentalCopyState.RETIRED;
      case OUT_OF_SERVICE -> RentalCopyState.OUT_OF_SERVICE;
      case USABLE ->
          use == Use.IN_USE
              ? RentalCopyState.IN_USE
              : use == Use.RESERVED ? RentalCopyState.RESERVED : RentalCopyState.AVAILABLE;
    };
  }
}
