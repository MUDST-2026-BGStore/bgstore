package com.chanakanlabs.theparlor.branches.internal.application;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.chanakanlabs.theparlor.branches.BranchAccess;
import com.chanakanlabs.theparlor.branches.internal.domain.BranchTable;
import com.chanakanlabs.theparlor.branches.internal.domain.TableOperationalStatus;
import com.chanakanlabs.theparlor.branches.internal.persistence.BranchTableRepository;
import com.chanakanlabs.theparlor.branches.spi.TableCommitments;
import com.chanakanlabs.theparlor.contracts.model.TableState;
import com.chanakanlabs.theparlor.contracts.model.UpdateTableRequest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.server.ResponseStatusException;

class TablesServiceTests {
  final UUID branchId = UUID.randomUUID();
  final UUID tableId = UUID.randomUUID();
  final Instant now = Instant.parse("2026-11-12T05:00:00Z");
  final BranchAccess access = mock(BranchAccess.class);
  final BranchTableRepository tables = mock(BranchTableRepository.class);
  final TableCommitments commitments = mock(TableCommitments.class);
  final TablesService service =
      new TablesService(access, tables, List.of(commitments), Clock.fixed(now, ZoneOffset.UTC));

  BranchTable table(TableOperationalStatus status) {
    var table = spy(BranchTable.create(branchId, "T1", 4, 5000, status));
    doReturn(tableId).when(table).getId();
    when(tables.findByIdAndBranchId(tableId, branchId)).thenReturn(Optional.of(table));
    when(tables.lockByIdAndBranchId(tableId, branchId)).thenReturn(Optional.of(table));
    return table;
  }

  @Test
  void derivesStateWithOccupancyAheadOfServiceStatusAndBookings() {
    table(TableOperationalStatus.IN_SERVICE);
    assertThat(service.get(branchId, tableId).getState()).isEqualTo(TableState.AVAILABLE);
    when(commitments.reservedTables(branchId, now)).thenReturn(Set.of(tableId));
    assertThat(service.get(branchId, tableId).getState()).isEqualTo(TableState.RESERVED);

    table(TableOperationalStatus.OUT_OF_SERVICE);
    assertThat(service.get(branchId, tableId).getState()).isEqualTo(TableState.OUT_OF_SERVICE);
    when(commitments.occupiedTables(branchId)).thenReturn(Set.of(tableId));
    assertThat(service.get(branchId, tableId).getState()).isEqualTo(TableState.OCCUPIED);
  }

  @Test
  void rejectsChangesThatWouldStrandACommitment() {
    var table = table(TableOperationalStatus.IN_SERVICE);
    when(commitments.hasPartyLargerThan(tableId, 2)).thenReturn(true);
    when(commitments.hasPartyLargerThan(tableId, 0)).thenReturn(true);

    assertThatThrownBy(
            () -> service.update(branchId, tableId, new UpdateTableRequest().capacity(2)))
        .isInstanceOfSatisfying(
            ResponseStatusException.class,
            failure -> assertThat(failure.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
    assertThatThrownBy(
            () ->
                service.update(
                    branchId,
                    tableId,
                    new UpdateTableRequest()
                        .operationalStatus(
                            UpdateTableRequest.OperationalStatusEnum.OUT_OF_SERVICE)))
        .isInstanceOf(ResponseStatusException.class);
    assertThat(table.getSeats()).isEqualTo(4);
    assertThat(table.isInService()).isTrue();
    verify(tables, never()).saveAndFlush(any());

    // Growing a table or keeping enough seats never strands anyone.
    assertThat(
            service.update(branchId, tableId, new UpdateTableRequest().capacity(3)).getCapacity())
        .isEqualTo(3);
    assertThatThrownBy(() -> service.update(branchId, tableId, new UpdateTableRequest()))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void writesRequireBranchOperatorAccessBeforeTouchingTheTable() {
    when(access.requireOperator(branchId)).thenThrow(new AccessDeniedException("denied"));
    assertThatThrownBy(
            () -> service.update(branchId, tableId, new UpdateTableRequest().capacity(2)))
        .isInstanceOf(AccessDeniedException.class);
    verifyNoInteractions(tables);
  }
}
