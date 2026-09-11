package com.chanakanlabs.bgstore.tables;

import com.chanakanlabs.bgstore.contract.api.TablesApi;
import com.chanakanlabs.bgstore.contract.model.CreateTableRequest;
import com.chanakanlabs.bgstore.contract.model.TableListResponse;
import com.chanakanlabs.bgstore.contract.model.TableResponse;
import com.chanakanlabs.bgstore.contract.model.TableShape;
import com.chanakanlabs.bgstore.contract.model.TableStatus;
import com.chanakanlabs.bgstore.contract.model.UpdateTableRequest;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class TableController implements TablesApi {

  private final TableManagementService tables;

  public TableController(TableManagementService tables) {
    this.tables = tables;
  }

  @Override
  public ResponseEntity<TableListResponse> listTables(
      @Nullable String branch,
      @Nullable String zone,
      @Nullable TableStatus status,
      @Nullable String search,
      Integer page,
      Integer pageSize) {
    String statusString = status != null ? status.getValue() : null;
    int pageNum = page != null ? page : 1;
    int size = pageSize != null ? pageSize : 20;

    var result = tables.listTables(branch, zone, statusString, search, pageNum, size);
    List<TableResponse> items = result.items().stream().map(TableController::toResponse).toList();
    TableListResponse response =
        new TableListResponse(
            items, result.total(), result.page(), result.pageSize(), result.totalPages());
    return ResponseEntity.ok(response);
  }

  @Override
  public ResponseEntity<TableResponse> getTable(Long tableId) {
    return ResponseEntity.ok(toResponse(tables.getTable(tableId)));
  }

  @Override
  public ResponseEntity<TableResponse> createTable(CreateTableRequest request) {
    TableRecordData created =
        tables.createTable(
            request.getName(),
            request.getBranch(),
            request.getCapacity(),
            request.getShape().getValue(),
            request.getStatus().getValue(),
            request.getActive(),
            request.getZone());
    return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(created));
  }

  @Override
  public ResponseEntity<TableResponse> updateTable(Long tableId, UpdateTableRequest request) {
    TableRecordData updated =
        tables.updateTable(
            tableId,
            request.getName(),
            request.getBranch(),
            request.getCapacity(),
            request.getShape().getValue(),
            request.getStatus().getValue(),
            request.getActive(),
            request.getZone());
    return ResponseEntity.ok(toResponse(updated));
  }

  @Override
  public ResponseEntity<Void> deleteTable(Long tableId) {
    tables.deleteTable(tableId);
    return ResponseEntity.noContent().build();
  }

  private static TableResponse toResponse(TableRecordData data) {
    return new TableResponse(
        java.util.Objects.requireNonNull(data.id(), "Table id must not be null"),
        data.name(),
        data.branch(),
        data.capacity(),
        TableShape.fromValue(data.shape()),
        TableStatus.fromValue(data.status()),
        data.active(),
        data.zone(),
        data.lastUpdated());
  }
}
