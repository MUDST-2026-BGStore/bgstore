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
import com.chanakanlabs.theparlor.shared.KeysetCursor;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Limit;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Branch offerings, current sale prices and stock. Catalog and branch state is read through their
 * public interfaces only; writes require branch-operator access.
 */
@Service
@Transactional(readOnly = true)
public class InventoryService {
  private static final String LIST = "branch-games";

  /** Offerings scanned per catalog round trip while filling a filtered page. */
  private static final int MIN_SCAN_SIZE = 50;

  private final BranchAccess branches;
  private final CatalogLookup catalog;
  private final BranchGameRepository offerings;
  private final BranchEditionRepository stock;
  private final SaleStockAdjustmentRepository adjustments;
  private final RentalCopyRepository copies;
  private final CopyUses copyUses;

  public InventoryService(
      BranchAccess branches,
      CatalogLookup catalog,
      BranchGameRepository offerings,
      BranchEditionRepository stock,
      SaleStockAdjustmentRepository adjustments,
      RentalCopyRepository copies,
      CopyUses copyUses) {
    this.branches = branches;
    this.catalog = catalog;
    this.offerings = offerings;
    this.stock = stock;
    this.adjustments = adjustments;
    this.copies = copies;
    this.copyUses = copyUses;
  }

  /** Public read: inactive branches and catalog titles are hidden as 404. */
  public BranchGame getBranchGame(UUID branchId, UUID gameId) {
    branches.requireActive(branchId);
    var game = catalog.activeGame(gameId).orElseThrow(InventoryService::missing);
    var offering =
        offerings
            .findById(new BranchGameId(branchId, gameId))
            .orElseThrow(InventoryService::missing);
    return describe(branchId, List.of(new Offered(offering, game))).getFirst();
  }

  /**
   * Pages the branch's offerings by game ID. Name search and catalog activity belong to the
   * catalog, so offerings are scanned in chunks and filtered through its public lookup until the
   * page is full; the cursor stays a plain position in the branch's own offerings.
   */
  public BranchGamePage listBranchGames(
      UUID branchId, Integer requestedLimit, @Nullable String cursor, @Nullable String query) {
    branches.requireActive(branchId);
    int limit = KeysetCursor.limit(requestedLimit);
    String name = query == null ? null : query.strip().toLowerCase(Locale.ROOT);
    if (name != null && name.isEmpty())
      throw new IllegalArgumentException("Search text cannot be blank");
    String filters = branchId + "|" + (name == null ? "" : name);
    UUID position = KeysetCursor.afterId(cursor, LIST, filters);

    var scan = Limit.of(Math.max(limit + 1, MIN_SCAN_SIZE));
    var matches = new ArrayList<Offered>();
    while (matches.size() <= limit) {
      var chunk =
          position == null
              ? offerings.firstOfBranch(branchId, scan)
              : offerings.nextOfBranch(branchId, position, scan);
      if (chunk.isEmpty()) break;
      Map<UUID, Game> games =
          catalog
              .activeGames(chunk.stream().map(offering -> offering.getId().gameId()).toList(), name)
              .stream()
              .collect(Collectors.toMap(Game::getId, Function.identity()));
      for (var offering : chunk) {
        var game = games.get(offering.getId().gameId());
        if (game != null && matches.size() <= limit) matches.add(new Offered(offering, game));
      }
      if (chunk.size() < scan.max()) break;
      position = chunk.getLast().getId().gameId();
    }

    var page = matches.stream().limit(limit).toList();
    var result = new BranchGamePage(describe(branchId, page));
    if (matches.size() > limit)
      result.setNextCursor(KeysetCursor.next(LIST, filters, page.getLast().game().getId()));
    return result;
  }

  /** Creates or replaces the offering flags; stock, copies and commitments are left untouched. */
  @Transactional
  public BranchGame configureBranchGame(
      UUID branchId, UUID gameId, ConfigureBranchGameRequest request) {
    branches.requireOperator(branchId);
    var game = catalog.activeGame(gameId).orElseThrow(InventoryService::missing);
    var id = new BranchGameId(branchId, gameId);
    var offering =
        offerings
            .lockById(id)
            .orElseGet(
                () -> com.chanakanlabs.theparlor.inventory.internal.domain.BranchGame.create(id));
    offering.configure(
        request.getSaleEnabled(),
        request.getRentalEnabled(),
        request.getStaffCanTeach(),
        request.getRentalHourlyRate().getAmountMinor());
    offerings.saveAndFlush(offering);
    return describe(branchId, List.of(new Offered(offering, game))).getFirst();
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
    return InventoryMapper.stock(
        row, edition, offering.isSaleEnabled() && Boolean.TRUE.equals(edition.getActive()));
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
    boolean sellable =
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
        InventoryMapper.saleAvailable(row, sellable),
        adjustment.getCreatedAt().atOffset(ZoneOffset.UTC),
        actor);
  }

  /** An offering paired with its active catalog title. */
  private record Offered(
      com.chanakanlabs.theparlor.inventory.internal.domain.BranchGame offering, Game game) {}

  /**
   * Builds responses for offerings at an active branch with batch reads: one for editions, one for
   * stock rows, one for copies and one per committing module, whatever the page size.
   */
  private List<BranchGame> describe(UUID branchId, List<Offered> page) {
    if (page.isEmpty()) return List.of();
    var gameIds = page.stream().map(offered -> offered.game().getId()).toList();

    var editions = catalog.activeEditions(gameIds);
    Map<UUID, BranchEdition> stockByEdition =
        stock
            .findByIdBranchIdAndIdEditionIdIn(
                branchId, editions.stream().map(GameEdition::getId).toList())
            .stream()
            .collect(Collectors.toMap(row -> row.getId().editionId(), Function.identity()));
    var copiesByGame =
        copies.findByBranchIdAndGameIdIn(branchId, gameIds).stream()
            .collect(
                Collectors.groupingBy(
                    com.chanakanlabs.theparlor.inventory.internal.domain.RentalCopy::getGameId));
    var uses = copyUses.now(branchId);

    var result = new ArrayList<BranchGame>();
    for (Offered offered : page) {
      var offering = offered.offering();
      UUID gameId = offered.game().getId();
      // Editions without a stock row have never been priced here, so they are not listed.
      var stocked = new ArrayList<BranchEditionStock>();
      for (GameEdition edition : editions) {
        var row = stockByEdition.get(edition.getId());
        if (row != null && edition.getGameId().equals(gameId))
          stocked.add(InventoryMapper.stock(row, edition, offering.isSaleEnabled()));
      }
      stocked.sort(java.util.Comparator.comparing(BranchEditionStock::getEditionId));

      var owned = copiesByGame.getOrDefault(gameId, List.of());
      var usable = owned.stream().filter(copy -> copy.isUsable()).toList();
      long retired =
          owned.stream()
              .filter(
                  copy ->
                      copy.getOperationalStatus()
                          == com.chanakanlabs.theparlor.inventory.internal.domain
                              .RentalCopyOperationalStatus.RETIRED)
              .count();
      // Advisory only: a booking must still revalidate and hold copies for its interval.
      long free =
          offering.isRentalEnabled()
              ? usable.stream().filter(copy -> !uses.containsKey(copy.getId())).count()
              : 0;
      result.add(
          new BranchGame(
              branchId,
              offered.game(),
              offering.isSaleEnabled(),
              offering.isRentalEnabled(),
              offering.isStaffCanTeach(),
              new Money(offering.getRentalHourlyRateMinor()),
              stocked,
              (int) (owned.size() - retired),
              usable.size(),
              (int) free));
    }
    return result;
  }

  private static ResponseStatusException missing() {
    return new ResponseStatusException(HttpStatus.NOT_FOUND, "Inventory item not found");
  }
}
