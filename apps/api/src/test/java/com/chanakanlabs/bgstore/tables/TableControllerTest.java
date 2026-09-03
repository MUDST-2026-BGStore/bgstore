package com.chanakanlabs.bgstore.tables;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.chanakanlabs.bgstore.contract.model.CreateTableRequest;
import com.chanakanlabs.bgstore.contract.model.TableShape;
import com.chanakanlabs.bgstore.contract.model.TableStatus;
import com.chanakanlabs.bgstore.contract.model.UpdateTableRequest;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class TableControllerTest {

  @Mock private TableManagementService service;

  private TableController controller;

  @BeforeEach
  void setUp() {
    controller = new TableController(service);
  }

  @Test
  void listsTables() {
    var record =
        new TableRecordData(
            1L,
            "Table 1",
            "Sukhumvit",
            4,
            "Round",
            "Available",
            true,
            "Main Hall",
            OffsetDateTime.now(ZoneOffset.UTC));
    when(service.listTables(
            eq("Sukhumvit"), eq("Main Hall"), eq("Available"), eq("query"), eq(1), eq(10)))
        .thenReturn(new TableManagementService.PageResult<>(List.of(record), 1, 1, 10, 1));

    var response =
        controller.listTables("Sukhumvit", "Main Hall", TableStatus.AVAILABLE, "query", 1, 10);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().getItems()).hasSize(1);
    assertThat(response.getBody().getItems().getFirst().getName()).isEqualTo("Table 1");
  }

  @Test
  void getsTable() {
    var record =
        new TableRecordData(
            1L,
            "Table 1",
            "Sukhumvit",
            4,
            "Round",
            "Available",
            true,
            "Main Hall",
            OffsetDateTime.now(ZoneOffset.UTC));
    when(service.getTable(1L)).thenReturn(record);

    var response = controller.getTable(1L);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().getName()).isEqualTo("Table 1");
  }

  @Test
  void createsTable() {
    var request =
        new CreateTableRequest(
            "New Table", "Sukhumvit", 6, TableShape.ROUND, TableStatus.AVAILABLE, true, "VIP");
    var record =
        new TableRecordData(
            100L,
            "New Table",
            "Sukhumvit",
            6,
            "Round",
            "Available",
            true,
            "VIP",
            OffsetDateTime.now(ZoneOffset.UTC));
    when(service.createTable("New Table", "Sukhumvit", 6, "Round", "Available", true, "VIP"))
        .thenReturn(record);

    var response = controller.createTable(request);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().getId()).isEqualTo(100L);
  }

  @Test
  void updatesTable() {
    var request =
        new UpdateTableRequest(
            "Updated", "Sukhumvit", 8, TableShape.SQUARE, TableStatus.RESERVED, true, "Main Hall");
    var record =
        new TableRecordData(
            1L,
            "Updated",
            "Sukhumvit",
            8,
            "Square",
            "Reserved",
            true,
            "Main Hall",
            OffsetDateTime.now(ZoneOffset.UTC));
    when(service.updateTable(
            1L, "Updated", "Sukhumvit", 8, "Square", "Reserved", true, "Main Hall"))
        .thenReturn(record);

    var response = controller.updateTable(1L, request);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().getName()).isEqualTo("Updated");
  }

  @Test
  void deletesTable() {
    var response = controller.deleteTable(1L);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    verify(service).deleteTable(1L);
  }
}
