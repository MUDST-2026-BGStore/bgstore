package com.chanakanlabs.theparlor.branches.internal.web;

import com.chanakanlabs.theparlor.branches.internal.application.TablesService;
import com.chanakanlabs.theparlor.contracts.api.TablesApi;
import com.chanakanlabs.theparlor.contracts.model.*;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TablesController implements TablesApi {
  private final TablesService tables;

  public TablesController(TablesService tables) {
    this.tables = tables;
  }

  @Override
  public Table createTable(UUID branchId, CreateTableRequest createTableRequest) {
    return tables.create(branchId, createTableRequest);
  }

  @Override
  public Table getTable(UUID branchId, UUID tableId) {
    return tables.get(branchId, tableId);
  }

  @Override
  public TablePage listTables(UUID branchId, Integer limit, @Nullable String cursor) {
    return tables.list(branchId, limit, cursor);
  }

  @Override
  public Table updateTable(UUID branchId, UUID tableId, UpdateTableRequest updateTableRequest) {
    return tables.update(branchId, tableId, updateTableRequest);
  }
}
