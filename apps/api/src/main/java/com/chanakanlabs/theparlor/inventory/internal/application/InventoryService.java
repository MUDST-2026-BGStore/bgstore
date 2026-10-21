package com.chanakanlabs.theparlor.inventory.internal.application;

import com.chanakanlabs.theparlor.contracts.model.*;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Branch offerings, current prices and physical inventory. All operations are unfinished. */
@Service
@Transactional(readOnly = true)
public class InventoryService {
  // TODO(inventory-access): use public catalog/branch interfaces to validate ownership and active
  // state. Add allowedDependencies only when those interfaces exist; never import their internals.
  // TODO(inventory-access): enforce branch-operator access on every write and copy-list operation:
  // administrators/managers can operate across branches; staff need explicit branch assignments.
  // TODO(inventory-mapping): map entities to generated response models in an internal mapper.
  // All amounts are integer THB satang; zero is a valid price, never a missing-price sentinel.

  public BranchGame getBranchGame(UUID branchId, UUID gameId) {
    // TODO(inventory-read): load the branch offering or return 404; join edition prices/stock and
    // derive rental counts. Hide inactive catalog entries according to the public access policy.
    // saleAvailable=max(0,onHand-held), gated by saleEnabled and active game/edition/branch.
    // rentalTotal excludes retired copies; rentalUsable includes occupied usable copies.
    // rentalAvailableNow is advisory; account for holds and active rentals using server time.
    throw new UnsupportedOperationException("Branch inventory reads are not implemented yet");
  }

  public BranchGamePage listBranchGames(
      UUID branchId, Integer limit, @Nullable String cursor, @Nullable String query) {
    // TODO(inventory-read): validate branch, limit and filter-bound cursor; use stable keyset
    // pagination and batch projections for the same price/count rules as getBranchGame.
    throw new UnsupportedOperationException("Branch inventory listing is not implemented yet");
  }

  @Transactional
  public BranchGame configureBranchGame(
      UUID branchId, UUID gameId, ConfigureBranchGameRequest request) {
    // TODO(inventory-offering): authorize operator, validate branch/game and nonnegative rate,
    // then create/update through BranchGame domain methods. Preserve stock, copies and existing
    // commitments when flags change. A new offering has no edition stock or rental copies.
    throw new UnsupportedOperationException("Branch offering configuration is not implemented yet");
  }

  @Transactional
  public BranchEditionStock upsertBranchEdition(
      UUID branchId, UUID editionId, UpsertBranchEditionRequest request) {
    // TODO(inventory-price): authorize operator; validate edition/game/branch and sale offering
    // through public module interfaces. Create with onHand=held=0, or change only the price.
    // Use domain methods and handle concurrent creation of the same branch/edition pair.
    // Existing order price snapshots must stay unchanged. Return the contract's 201/200 status.
    throw new UnsupportedOperationException("Edition sale pricing is not implemented yet");
  }

  @Transactional
  public StockAdjustment adjustSaleStock(
      UUID branchId, UUID editionId, StockAdjustmentRequest request) {
    // TODO(inventory-stock): authorize operator; require an existing branch price, nonzero delta
    // and nonblank reason. Lock the same stock row used by SaleInventoryService and reject
    // onHand+delta<held or integer overflow. Mutate through BranchEdition domain methods.
    // Append SaleStockAdjustment with server actor/time in this transaction; rollback both on
    // error.
    // Never add rental copies or change the sale price through a stock adjustment.
    throw new UnsupportedOperationException("Sale stock adjustments are not implemented yet");
  }

  public RentalCopyPage listRentalCopies(
      UUID branchId, UUID gameId, Integer limit, @Nullable String cursor) {
    // TODO(inventory-copies): authorize operator, validate branch/game and cursor, and paginate
    // copies. Derive state from operational status, active rentals and confirmed holds covering
    // now.
    throw new UnsupportedOperationException("Rental copy listing is not implemented yet");
  }

  @Transactional
  public RentalCopy createRentalCopy(UUID branchId, UUID gameId, CreateRentalCopyRequest request) {
    // TODO(inventory-copies): authorize operator, validate ownership and register a USABLE copy
    // through a domain factory. Enforce branch-wide unique asset codes; translate duplicates to
    // 409.
    // TODO(inventory-contract): resolve assetCode maxLength=80 versus storage/domain width=64
    // in OpenAPI and, if widening storage, a new migration; regenerate bindings after contract
    // edits.
    throw new UnsupportedOperationException("Rental copy registration is not implemented yet");
  }

  @Transactional
  public RentalCopy updateRentalCopy(
      UUID branchId, UUID gameId, UUID copyId, UpdateRentalCopyRequest request) {
    // TODO(inventory-copies): authorize operator and verify all three IDs match; require a reason
    // for OUT_OF_SERVICE/RETIRED and prevent reactivating retired copies through domain methods.
    // Lock against booking/check-in. Reject with 409 unless a replacement usable copy can cover
    // every affected commitment atomically. Never let clients set derived availability directly.
    // TODO(inventory-schema): persist condition-change actor/time/reason with a new migration;
    // the response exposes a latest reason, but rental_copies currently has no reason column.
    throw new UnsupportedOperationException(
        "Rental copy condition updates are not implemented yet");
  }
}
