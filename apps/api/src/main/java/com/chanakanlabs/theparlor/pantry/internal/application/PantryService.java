package com.chanakanlabs.theparlor.pantry.internal.application;

import com.chanakanlabs.theparlor.branches.BranchAccess;
import com.chanakanlabs.theparlor.pantry.Pantry;
import com.chanakanlabs.theparlor.pantry.internal.domain.BranchMenuStock;
import com.chanakanlabs.theparlor.pantry.internal.domain.BranchMenuStockId;
import com.chanakanlabs.theparlor.pantry.internal.domain.MenuItem;
import com.chanakanlabs.theparlor.pantry.internal.persistence.BranchMenuStockRepository;
import com.chanakanlabs.theparlor.pantry.internal.persistence.MenuItemRepository;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Rows are always locked in menu item ID order, so concurrent pre-orders cannot deadlock. */
@Service
@Transactional(propagation = Propagation.MANDATORY)
public class PantryService implements Pantry {
  /** Largest quantity of one item in one commitment, matching PreOrderItemRequest. */
  public static final int MAX_QUANTITY = 50;

  private final BranchMenuStockRepository stock;
  private final MenuItemRepository menu;
  private final BranchAccess branches;

  public PantryService(
      BranchMenuStockRepository stock, MenuItemRepository menu, BranchAccess branches) {
    this.stock = stock;
    this.menu = menu;
    this.branches = branches;
  }

  @Override
  public List<PricedItem> hold(UUID branchId, List<Selection> selections) {
    branches.requireActive(branchId);
    var sorted = validate(selections);
    Map<UUID, MenuItem> items =
        menu.findByIdIn(sorted.stream().map(Selection::menuItemId).toList()).stream()
            .filter(MenuItem::isActive)
            .collect(Collectors.toMap(MenuItem::getId, Function.identity()));
    var priced = new ArrayList<PricedItem>();
    for (var selection : sorted) {
      var item = items.get(selection.menuItemId());
      if (item == null) throw unavailable();
      apply(locked(branchId, selection.menuItemId()), row -> row.hold(selection.quantity()));
      priced.add(
          new PricedItem(
              item.getId(), item.defaultName(), selection.quantity(), item.getPriceMinor()));
    }
    return priced;
  }

  @Override
  public void release(UUID branchId, List<Selection> selections) {
    for (var selection : validate(selections))
      apply(locked(branchId, selection.menuItemId()), row -> row.release(selection.quantity()));
  }

  @Override
  public void fulfill(UUID branchId, List<Selection> selections) {
    for (var selection : validate(selections))
      apply(locked(branchId, selection.menuItemId()), row -> row.fulfill(selection.quantity()));
  }

  private BranchMenuStock locked(UUID branchId, UUID menuItemId) {
    return stock
        .lockById(new BranchMenuStockId(branchId, menuItemId))
        .orElseThrow(PantryService::unavailable);
  }

  private static void apply(BranchMenuStock row, java.util.function.Consumer<BranchMenuStock> op) {
    try {
      op.accept(row);
    } catch (IllegalArgumentException | ArithmeticException exception) {
      throw unavailable();
    }
  }

  static List<Selection> validate(List<Selection> selections) {
    if (selections == null || selections.isEmpty())
      throw new IllegalArgumentException("Menu selections are required");
    var seen = new HashSet<UUID>();
    for (var selection : selections)
      if (selection == null
          || selection.menuItemId() == null
          || selection.quantity() < 1
          || selection.quantity() > MAX_QUANTITY
          || !seen.add(selection.menuItemId()))
        throw new IllegalArgumentException("Invalid or duplicate menu selection");
    return selections.stream().sorted(Comparator.comparing(Selection::menuItemId)).toList();
  }

  private static ResponseStatusException unavailable() {
    return new ResponseStatusException(
        HttpStatus.CONFLICT, "Some food or drinks are not available at this branch");
  }
}
