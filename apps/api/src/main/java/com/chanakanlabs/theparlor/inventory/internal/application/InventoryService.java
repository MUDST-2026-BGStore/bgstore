package com.chanakanlabs.theparlor.inventory.internal.application;

import com.chanakanlabs.theparlor.branches.BranchAccess;
import com.chanakanlabs.theparlor.catalog.CatalogLookup;
import com.chanakanlabs.theparlor.contracts.model.*;
import com.chanakanlabs.theparlor.inventory.internal.domain.BranchEdition;
import com.chanakanlabs.theparlor.inventory.internal.domain.BranchEditionId;
import com.chanakanlabs.theparlor.inventory.internal.domain.BranchGameId;
import com.chanakanlabs.theparlor.inventory.internal.domain.SaleStockAdjustment;
import com.chanakanlabs.theparlor.inventory.internal.persistence.BranchEditionRepository;
import com.chanakanlabs.theparlor.inventory.internal.persistence.BranchGameRepository;
import com.chanakanlabs.theparlor.inventory.internal.persistence.RentalCopyRepository;
import com.chanakanlabs.theparlor.inventory.internal.persistence.SaleStockAdjustmentRepository;
import java.time.ZoneOffset;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Branch offerings, current prices and physical inventory. All operations are unfinished. */
@Service
@Transactional(readOnly = true)
public class InventoryService {
  private final BranchAccess branches;
  private final CatalogLookup catalog;
  private final BranchGameRepository offerings;
  private final BranchEditionRepository stock;
  private final SaleStockAdjustmentRepository adjustments;
  private final RentalCopyRepository copies;

  public InventoryService(
      BranchAccess branches,
      CatalogLookup catalog,
      BranchGameRepository offerings,
      BranchEditionRepository stock,
      SaleStockAdjustmentRepository adjustments,
      RentalCopyRepository copies) {
    this.branches = branches;
    this.catalog = catalog;
    this.offerings = offerings;
    this.stock = stock;
    this.adjustments = adjustments;
    this.copies = copies;
  }

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
    branches.requireOperator(branchId);
    var edition = catalog.edition(editionId).orElseThrow(InventoryService::missing);
    if (catalog.activeGame(edition.getGameId()).isEmpty()) throw missing();
    var offering =
        offerings
            .lockById(new BranchGameId(branchId, edition.getGameId()))
            .orElseThrow(InventoryService::missing);
    if (!offering.isSaleEnabled()) throw missing();
    var id = new BranchEditionId(branchId, editionId);
    var row = stock.lockById(id).orElse(null);
    long price = request.getSalePrice().getAmountMinor();
    if (row == null) row = stock.save(BranchEdition.create(id, price));
    else row.changePrice(price);
    return mapStock(row, edition, offering.isSaleEnabled());
  }

  @Transactional
  public StockAdjustment adjustSaleStock(
      UUID branchId, UUID editionId, StockAdjustmentRequest request) {
    String actor = branches.requireOperator(branchId);
    var edition = catalog.edition(editionId).orElseThrow(InventoryService::missing);
    var offering =
        offerings
            .lockById(new BranchGameId(branchId, edition.getGameId()))
            .orElseThrow(InventoryService::missing);
    var row =
        stock
            .lockById(new BranchEditionId(branchId, editionId))
            .orElseThrow(InventoryService::missing);
    var adjustment =
        SaleStockAdjustment.create(
            branchId, editionId, request.getDelta(), request.getReason(), actor);
    try {
      row.adjustStock(request.getDelta());
    } catch (ArithmeticException | IllegalArgumentException ex) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "Stock adjustment exceeds available units", ex);
    }
    adjustments.saveAndFlush(adjustment);
    boolean available =
        offering.isSaleEnabled()
            && Boolean.TRUE.equals(edition.getActive())
            && catalog.activeGame(edition.getGameId()).isPresent();
    return new StockAdjustment(
        adjustment.getId(),
        branchId,
        editionId,
        adjustment.getDelta(),
        adjustment.getReason(),
        row.getSaleOnHand(),
        available ? row.getSaleOnHand() - row.getSaleHeld() : 0,
        adjustment.getCreatedAt().atOffset(ZoneOffset.UTC),
        actor);
  }

  private static BranchEditionStock mapStock(
      BranchEdition row, GameEdition edition, boolean saleEnabled) {
    boolean available = saleEnabled && Boolean.TRUE.equals(edition.getActive());
    return new BranchEditionStock(
        edition.getId(),
        BranchEditionStock.EditionKindEnum.fromValue(edition.getEditionKind().getValue()),
        edition.getLanguageCode(),
        edition.getActive(),
        new Money(row.getSalePriceMinor()),
        row.getSaleOnHand(),
        row.getSaleHeld(),
        available ? row.getSaleOnHand() - row.getSaleHeld() : 0);
  }

  private static ResponseStatusException missing() {
    return new ResponseStatusException(HttpStatus.NOT_FOUND, "Inventory item not found");
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
    branches.requireOperator(branchId);
    if (catalog.activeGame(gameId).isEmpty()) throw missing();
    offerings.lockById(new BranchGameId(branchId, gameId)).orElseThrow(InventoryService::missing);
    var copy =
        com.chanakanlabs.theparlor.inventory.internal.domain.RentalCopy.register(
            branchId, gameId, request.getAssetCode());
    try {
      copies.saveAndFlush(copy);
    } catch (DataIntegrityViolationException ex) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "Asset code already exists at this branch", ex);
    }
    return new RentalCopy(
        copy.getId(),
        branchId,
        gameId,
        copy.getAssetCode(),
        RentalCopyOperationalStatus.USABLE,
        RentalCopyState.AVAILABLE);
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
