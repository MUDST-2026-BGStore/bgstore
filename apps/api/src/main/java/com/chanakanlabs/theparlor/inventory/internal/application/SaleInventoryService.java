package com.chanakanlabs.theparlor.inventory.internal.application;

import com.chanakanlabs.theparlor.branches.BranchAccess;
import com.chanakanlabs.theparlor.catalog.CatalogLookup;
import com.chanakanlabs.theparlor.inventory.SaleInventory;
import com.chanakanlabs.theparlor.inventory.internal.domain.BranchEdition;
import com.chanakanlabs.theparlor.inventory.internal.domain.BranchEditionId;
import com.chanakanlabs.theparlor.inventory.internal.domain.BranchGameId;
import com.chanakanlabs.theparlor.inventory.internal.persistence.BranchEditionRepository;
import com.chanakanlabs.theparlor.inventory.internal.persistence.BranchGameRepository;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional(propagation = Propagation.MANDATORY)
public class SaleInventoryService implements SaleInventory {
  private final BranchEditionRepository stock;
  private final BranchGameRepository offerings;
  private final CatalogLookup catalog;
  private final BranchAccess branches;

  public SaleInventoryService(
      BranchEditionRepository stock,
      BranchGameRepository offerings,
      CatalogLookup catalog,
      BranchAccess branches) {
    this.stock = stock;
    this.offerings = offerings;
    this.catalog = catalog;
    this.branches = branches;
  }

  @Override
  public List<PricedItem> hold(UUID branchId, List<Selection> selections) {
    branches.requireActive(branchId);
    var sorted = validate(selections);
    var result = new ArrayList<PricedItem>();
    // Lock offerings before stock, consistently with price changes.
    var editions = new HashMap<UUID, com.chanakanlabs.theparlor.contracts.model.GameEdition>();
    for (var selection : sorted) {
      var edition =
          catalog
              .edition(selection.editionId())
              .filter(e -> Boolean.TRUE.equals(e.getActive()))
              .orElseThrow(SaleInventoryService::conflict);
      editions.put(selection.editionId(), edition);
    }
    for (var gameId :
        editions.values().stream().map(e -> e.getGameId()).distinct().sorted().toList()) {
      if (catalog.activeGame(gameId).isEmpty()
          || offerings
              .lockById(new BranchGameId(branchId, gameId))
              .filter(g -> g.isSaleEnabled())
              .isEmpty()) throw conflict();
    }
    for (var selection : sorted) {
      var row = locked(branchId, selection.editionId());
      try {
        row.hold(selection.quantity());
      } catch (IllegalArgumentException | ArithmeticException ex) {
        throw conflict();
      }
      var game =
          catalog
              .activeGame(editions.get(selection.editionId()).getGameId())
              .orElseThrow(SaleInventoryService::conflict);
      result.add(
          new PricedItem(
              selection.editionId(),
              game.getName(),
              selection.quantity(),
              row.getSalePriceMinor()));
    }
    return result;
  }

  @Override
  public void release(UUID branchId, List<Selection> selections) {
    mutate(branchId, selections, false);
  }

  @Override
  public void fulfill(UUID branchId, List<Selection> selections) {
    mutate(branchId, selections, true);
  }

  private void mutate(UUID branchId, List<Selection> selections, boolean fulfill) {
    for (var item : validate(selections)) {
      BranchEdition row = locked(branchId, item.editionId());
      try {
        if (fulfill) row.fulfill(item.quantity());
        else row.release(item.quantity());
      } catch (IllegalArgumentException ex) {
        throw conflict();
      }
    }
  }

  private BranchEdition locked(UUID branchId, UUID editionId) {
    return stock
        .lockById(new BranchEditionId(branchId, editionId))
        .orElseThrow(SaleInventoryService::conflict);
  }

  private static List<Selection> validate(List<Selection> selections) {
    if (selections == null || selections.isEmpty())
      throw new IllegalArgumentException("Selections required");
    var seen = new HashSet<UUID>();
    for (var selection : selections)
      if (selection == null
          || selection.editionId() == null
          || selection.quantity() <= 0
          || selection.quantity() > 100
          || !seen.add(selection.editionId()))
        throw new IllegalArgumentException("Invalid or duplicate selection");
    return selections.stream().sorted(Comparator.comparing(Selection::editionId)).toList();
  }

  private static ResponseStatusException conflict() {
    return new ResponseStatusException(HttpStatus.CONFLICT, "Sale stock unavailable");
  }
}
