package com.chanakanlabs.theparlor.branches.internal.web;

import com.chanakanlabs.theparlor.contracts.api.TablesApi;
import com.chanakanlabs.theparlor.contracts.model.*;
import java.time.*;
import java.util.*;
import org.jspecify.annotations.Nullable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TablesController implements TablesApi {

  @Override
  public Table createTable(UUID branchId, CreateTableRequest createTableRequest) {
    // TODO(branches-api): implement createTable — POST /api/v1/branches/{branchId}/tables — Add a
    // table to a branch
    throw new UnsupportedOperationException(
        "POST /api/v1/branches/{branchId}/tables (createTable) is not implemented yet");
  }

  @Override
  public Table getTable(UUID branchId, UUID tableId) {
    // TODO(branches-api): implement getTable — GET /api/v1/branches/{branchId}/tables/{tableId} —
    // Read table capacity, pricing and current state
    throw new UnsupportedOperationException(
        "GET /api/v1/branches/{branchId}/tables/{tableId} (getTable) is not implemented yet");
  }

  @Override
  public TablePage listTables(UUID branchId, Integer limit, @Nullable String cursor) {
    // TODO(branches-api): implement listTables — GET /api/v1/branches/{branchId}/tables — List
    // tables and their current derived state
    throw new UnsupportedOperationException(
        "GET /api/v1/branches/{branchId}/tables (listTables) is not implemented yet");
  }

  @Override
  public Table updateTable(UUID branchId, UUID tableId, UpdateTableRequest updateTableRequest) {
    // TODO(branches-api): implement updateTable — PATCH
    // /api/v1/branches/{branchId}/tables/{tableId} — Update table configuration or take it out of
    // service
    throw new UnsupportedOperationException(
        "PATCH /api/v1/branches/{branchId}/tables/{tableId} (updateTable) is not implemented yet");
  }
}
