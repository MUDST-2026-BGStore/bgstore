package com.chanakanlabs.theparlor.pantry.internal.application;

import com.chanakanlabs.theparlor.branches.BranchAccess;
import com.chanakanlabs.theparlor.contracts.model.BranchMenu;
import com.chanakanlabs.theparlor.contracts.model.BranchMenuItem;
import com.chanakanlabs.theparlor.contracts.model.MenuCategory;
import com.chanakanlabs.theparlor.contracts.model.MenuStock;
import com.chanakanlabs.theparlor.contracts.model.Money;
import com.chanakanlabs.theparlor.contracts.model.StockAdjustmentRequest;
import com.chanakanlabs.theparlor.pantry.internal.domain.BranchMenuStock;
import com.chanakanlabs.theparlor.pantry.internal.domain.BranchMenuStockId;
import com.chanakanlabs.theparlor.pantry.internal.domain.MenuItem;
import com.chanakanlabs.theparlor.pantry.internal.domain.MenuStockAdjustment;
import com.chanakanlabs.theparlor.pantry.internal.persistence.BranchMenuStockRepository;
import com.chanakanlabs.theparlor.pantry.internal.persistence.MenuItemRepository;
import com.chanakanlabs.theparlor.pantry.internal.persistence.MenuStockAdjustmentRepository;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** The public menu read and staff pantry receipts. */
@Service
@Transactional(readOnly = true)
public class MenuService {
  private final MenuItemRepository menu;
  private final BranchMenuStockRepository stock;
  private final MenuStockAdjustmentRepository adjustments;
  private final BranchAccess branches;

  MenuService(
      MenuItemRepository menu,
      BranchMenuStockRepository stock,
      MenuStockAdjustmentRepository adjustments,
      BranchAccess branches) {
    this.menu = menu;
    this.stock = stock;
    this.adjustments = adjustments;
    this.branches = branches;
  }

  /** Unknown and inactive branches both answer 404, as for other public branch reads. */
  public BranchMenu branchMenu(UUID branchId) {
    if (!branches.isActive(branchId))
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Branch not found");
    Map<UUID, BranchMenuStock> rows =
        stock.findByIdBranchId(branchId).stream()
            .collect(Collectors.toMap(row -> row.getId().menuItemId(), Function.identity()));
    var items =
        menu.findByActiveTrueOrderByCategoryAscSkuAsc().stream()
            .map(
                item -> {
                  var row = rows.get(item.getId());
                  return new BranchMenuItem(view(item), row == null ? 0 : row.available());
                })
            .toList();
    return new BranchMenu(branchId, items);
  }

  /** Inactive items can still be counted, so a returning item keeps its stock history. */
  @Transactional
  public MenuStock adjust(UUID branchId, UUID menuItemId, StockAdjustmentRequest request) {
    String actor = branches.requireOperator(branchId);
    if (!menu.existsById(menuItemId))
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Menu item not found");
    var id = new BranchMenuStockId(branchId, menuItemId);
    var adjustment = MenuStockAdjustment.record(id, request.getDelta(), request.getReason(), actor);
    stock.insertEmptyIfAbsent(branchId, menuItemId);
    var row = stock.lockById(id).orElseThrow();
    try {
      row.adjust(request.getDelta());
    } catch (IllegalArgumentException | ArithmeticException exception) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "Stock cannot fall below the units held by pre-orders", exception);
    }
    stock.flush();
    adjustments.saveAndFlush(adjustment);
    return new MenuStock(branchId, menuItemId, row.getOnHand(), row.getHeld(), row.available());
  }

  private static com.chanakanlabs.theparlor.contracts.model.MenuItem view(MenuItem item) {
    var result =
        new com.chanakanlabs.theparlor.contracts.model.MenuItem(
            item.getId(),
            item.getSku(),
            MenuCategory.valueOf(item.getCategory().name()),
            new HashMap<>(item.getNames()),
            new Money(item.getPriceMinor()),
            item.isPopular());
    if (!item.getDescriptions().isEmpty())
      result.setDescriptions(new HashMap<>(item.getDescriptions()));
    result.setDietary(item.getDietary());
    return result;
  }
}
