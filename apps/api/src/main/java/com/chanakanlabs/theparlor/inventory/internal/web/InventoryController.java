package com.chanakanlabs.theparlor.inventory.internal.web;

import com.chanakanlabs.theparlor.contracts.api.InventoryApi;
import com.chanakanlabs.theparlor.contracts.model.*;
import com.chanakanlabs.theparlor.inventory.internal.application.InventoryService;
import com.chanakanlabs.theparlor.inventory.internal.application.RentalCopiesService;
import java.time.*;
import java.util.*;
import org.jspecify.annotations.Nullable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class InventoryController implements InventoryApi {
  private final InventoryService inventory;
  private final RentalCopiesService rentalCopies;

  public InventoryController(InventoryService inventory, RentalCopiesService rentalCopies) {
    this.inventory = inventory;
    this.rentalCopies = rentalCopies;
  }

  @Override
  public StockAdjustment adjustSaleStock(
      UUID branchId, UUID editionId, StockAdjustmentRequest stockAdjustmentRequest) {
    return inventory.adjustSaleStock(branchId, editionId, stockAdjustmentRequest);
  }

  @Override
  public BranchGame configureBranchGame(
      UUID branchId, UUID gameId, ConfigureBranchGameRequest configureBranchGameRequest) {
    return inventory.configureBranchGame(branchId, gameId, configureBranchGameRequest);
  }

  @Override
  public RentalCopy createRentalCopy(
      UUID branchId, UUID gameId, CreateRentalCopyRequest createRentalCopyRequest) {
    return rentalCopies.create(branchId, gameId, createRentalCopyRequest);
  }

  @Override
  public BranchGame getBranchGame(UUID branchId, UUID gameId) {
    return inventory.getBranchGame(branchId, gameId);
  }

  @Override
  public BranchGamePage listBranchGames(
      UUID branchId, Integer limit, @Nullable String cursor, @Nullable String q) {
    return inventory.listBranchGames(branchId, limit, cursor, q);
  }

  @Override
  public RentalCopyPage listRentalCopies(
      UUID branchId, UUID gameId, Integer limit, @Nullable String cursor) {
    return rentalCopies.list(branchId, gameId, limit, cursor);
  }

  @Override
  public RentalCopy updateRentalCopy(
      UUID branchId, UUID gameId, UUID copyId, UpdateRentalCopyRequest updateRentalCopyRequest) {
    return rentalCopies.update(branchId, gameId, copyId, updateRentalCopyRequest);
  }

  @Override
  public BranchEditionStock upsertBranchEdition(
      UUID branchId, UUID editionId, UpsertBranchEditionRequest upsertBranchEditionRequest) {
    return inventory.upsertBranchEdition(branchId, editionId, upsertBranchEditionRequest);
  }
}
