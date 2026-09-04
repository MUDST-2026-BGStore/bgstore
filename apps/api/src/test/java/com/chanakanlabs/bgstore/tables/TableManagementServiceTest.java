package com.chanakanlabs.bgstore.tables;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import com.chanakanlabs.bgstore.identity.AccessPolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class TableManagementServiceTest {

  @Mock private AccessPolicy accessPolicy;

  private TableRepository repository;
  private TableManagementService service;

  @BeforeEach
  void setUp() {
    repository = new InMemoryTableRepository();
    service = new TableManagementService(repository, accessPolicy);
  }

  @Test
  void listsTablesWithPaginationAndFilters() {
    var result = service.listTables("Sukhumvit", "Main Hall", "Available", "", 1, 5);

    verify(accessPolicy).requireStaffOrManager();
    assertThat(result.items()).isNotEmpty();
    assertThat(result.items()).allMatch(t -> "Sukhumvit".equalsIgnoreCase(t.branch()));
    assertThat(result.items()).allMatch(t -> "Main Hall".equalsIgnoreCase(t.zone()));
    assertThat(result.items()).allMatch(t -> "Available".equalsIgnoreCase(t.status()));
    assertThat(result.pageSize()).isEqualTo(5);
  }

  @Test
  void listsTablesSearchFilter() {
    var result = service.listTables(null, null, null, "Table 12", 1, 10);

    assertThat(result.items()).hasSize(1);
    assertThat(result.items().getFirst().name()).isEqualTo("Table 12");
  }

  @Test
  void getsTableById() {
    var table = service.getTable(1L);

    verify(accessPolicy).requireStaffOrManager();
    assertThat(table.id()).isEqualTo(1L);
    assertThat(table.name()).isEqualTo("Table 1");
  }

  @Test
  void throwsNotFoundWhenTableDoesNotExist() {
    assertThatThrownBy(() -> service.getTable(9999L))
        .isInstanceOf(ResponseStatusException.class)
        .satisfies(
            e ->
                assertThat(((ResponseStatusException) e).getStatusCode())
                    .isEqualTo(HttpStatus.NOT_FOUND));
  }

  @Test
  void createsTableSuccessfully() {
    var created =
        service.createTable("New VIP Table", "Sukhumvit", 8, "Round", "Available", true, "VIP");

    verify(accessPolicy).requireStaffOrManager();
    assertThat(created.id()).isNotNull();
    assertThat(created.name()).isEqualTo("New VIP Table");
    assertThat(created.capacity()).isEqualTo(8);
    assertThat(repository.findById(created.id())).isPresent();
  }

  @Test
  void throwsBadRequestWhenCreatingDuplicateNameInSameBranch() {
    assertThatThrownBy(
            () ->
                service.createTable(
                    "Table 1", "Sukhumvit", 4, "Round", "Available", true, "Main Hall"))
        .isInstanceOf(ResponseStatusException.class)
        .satisfies(
            e ->
                assertThat(((ResponseStatusException) e).getStatusCode())
                    .isEqualTo(HttpStatus.BAD_REQUEST));
  }

  @Test
  void throwsBadRequestOnInvalidCapacityOrBlankFields() {
    assertThatThrownBy(
            () -> service.createTable("", "Sukhumvit", 4, "Round", "Available", true, "Main Hall"))
        .isInstanceOf(ResponseStatusException.class)
        .satisfies(
            e ->
                assertThat(((ResponseStatusException) e).getStatusCode())
                    .isEqualTo(HttpStatus.BAD_REQUEST));

    assertThatThrownBy(
            () ->
                service.createTable(
                    "Valid", "Sukhumvit", 0, "Round", "Available", true, "Main Hall"))
        .isInstanceOf(ResponseStatusException.class)
        .satisfies(
            e ->
                assertThat(((ResponseStatusException) e).getStatusCode())
                    .isEqualTo(HttpStatus.BAD_REQUEST));
  }

  @Test
  void updatesTableSuccessfully() {
    var updated =
        service.updateTable(
            1L, "Table 1 Renamed", "Sukhumvit", 6, "Square", "Occupied", true, "Main Hall");

    assertThat(updated.name()).isEqualTo("Table 1 Renamed");
    assertThat(updated.capacity()).isEqualTo(6);
    assertThat(updated.status()).isEqualTo("Occupied");

    var fetched = service.getTable(1L);
    assertThat(fetched.name()).isEqualTo("Table 1 Renamed");
  }

  @Test
  void throwsNotFoundWhenUpdatingNonExistentTable() {
    assertThatThrownBy(
            () ->
                service.updateTable(
                    9999L, "Ghost", "Sukhumvit", 4, "Round", "Available", true, "VIP"))
        .isInstanceOf(ResponseStatusException.class)
        .satisfies(
            e ->
                assertThat(((ResponseStatusException) e).getStatusCode())
                    .isEqualTo(HttpStatus.NOT_FOUND));
  }

  @Test
  void deletesTableSuccessfully() {
    service.deleteTable(1L);

    verify(accessPolicy).requireStaffOrManager();
    assertThat(repository.findById(1L)).isEmpty();
  }

  @Test
  void throwsNotFoundWhenDeletingNonExistentTable() {
    assertThatThrownBy(() -> service.deleteTable(9999L))
        .isInstanceOf(ResponseStatusException.class)
        .satisfies(
            e ->
                assertThat(((ResponseStatusException) e).getStatusCode())
                    .isEqualTo(HttpStatus.NOT_FOUND));
  }

  @Test
  void enforcesAccessPolicy() {
    doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "Forbidden"))
        .when(accessPolicy)
        .requireStaffOrManager();

    assertThatThrownBy(() -> service.listTables(null, null, null, null, 1, 10))
        .isInstanceOf(ResponseStatusException.class)
        .satisfies(
            e ->
                assertThat(((ResponseStatusException) e).getStatusCode())
                    .isEqualTo(HttpStatus.FORBIDDEN));
  }
}
