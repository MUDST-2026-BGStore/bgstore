package com.chanakanlabs.theparlor.pantry.internal.web;

import com.chanakanlabs.theparlor.contracts.api.PantryApi;
import com.chanakanlabs.theparlor.contracts.model.BranchMenu;
import com.chanakanlabs.theparlor.contracts.model.MenuStock;
import com.chanakanlabs.theparlor.contracts.model.StockAdjustmentRequest;
import com.chanakanlabs.theparlor.pantry.internal.application.MenuService;
import java.util.UUID;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PantryController implements PantryApi {
  private final MenuService menu;

  public PantryController(MenuService menu) {
    this.menu = menu;
  }

  @Override
  public BranchMenu getBranchMenu(UUID branchId) {
    return menu.branchMenu(branchId);
  }

  @Override
  public MenuStock adjustMenuStock(
      UUID branchId, UUID menuItemId, StockAdjustmentRequest stockAdjustmentRequest) {
    return menu.adjust(branchId, menuItemId, stockAdjustmentRequest);
  }
}
