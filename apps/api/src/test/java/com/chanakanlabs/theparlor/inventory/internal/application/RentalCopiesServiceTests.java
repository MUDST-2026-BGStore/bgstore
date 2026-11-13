package com.chanakanlabs.theparlor.inventory.internal.application;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.chanakanlabs.theparlor.branches.BranchAccess;
import com.chanakanlabs.theparlor.catalog.CatalogLookup;
import com.chanakanlabs.theparlor.contracts.model.RentalCopyState;
import com.chanakanlabs.theparlor.contracts.model.UpdateRentalCopyRequest;
import com.chanakanlabs.theparlor.inventory.internal.domain.RentalCopy;
import com.chanakanlabs.theparlor.inventory.internal.domain.RentalCopyOperationalStatus;
import com.chanakanlabs.theparlor.inventory.internal.persistence.*;
import com.chanakanlabs.theparlor.inventory.spi.CopyCommitments;
import com.chanakanlabs.theparlor.inventory.spi.CopyCommitments.Commitment;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class RentalCopiesServiceTests {
  final UUID branchId = UUID.randomUUID();
  final UUID gameId = UUID.randomUUID();
  final Instant now = Instant.parse("2026-11-12T05:00:00Z");
  final BranchAccess access = mock(BranchAccess.class);
  final RentalCopyRepository copies = mock(RentalCopyRepository.class);
  final RentalCopyConditionChangeRepository changes =
      mock(RentalCopyConditionChangeRepository.class);
  final CopyCommitments rentals = mock(CopyCommitments.class);
  final CopyCommitments holds = mock(CopyCommitments.class);
  final RentalCopiesService service =
      new RentalCopiesService(
          access,
          mock(CatalogLookup.class),
          mock(BranchGameRepository.class),
          copies,
          changes,
          List.of(rentals, holds),
          new CopyUses(List.of(rentals, holds), Clock.fixed(now, ZoneOffset.UTC)));

  RentalCopy copy(String code) {
    var copy = spy(RentalCopy.register(branchId, gameId, code));
    doReturn(UUID.randomUUID()).when(copy).getId();
    return copy;
  }

  static UpdateRentalCopyRequest to(
      com.chanakanlabs.theparlor.contracts.model.RentalCopyOperationalStatus status,
      String reason) {
    return new UpdateRentalCopyRequest(status).reason(reason);
  }

  static final com.chanakanlabs.theparlor.contracts.model.RentalCopyOperationalStatus
      OUT_OF_SERVICE =
          com.chanakanlabs.theparlor.contracts.model.RentalCopyOperationalStatus.OUT_OF_SERVICE,
      USABLE = com.chanakanlabs.theparlor.contracts.model.RentalCopyOperationalStatus.USABLE,
      RETIRED = com.chanakanlabs.theparlor.contracts.model.RentalCopyOperationalStatus.RETIRED;

  @Test
  void movesEveryCommitmentToACopyThatIsFreeInAllModulesBeforeLeavingThePool() {
    var damaged = copy("A");
    var busy = copy("B");
    var spare = copy("C");
    var broken = copy("D");
    broken.changeCondition(RentalCopyOperationalStatus.OUT_OF_SERVICE);
    when(access.requireOperator(branchId)).thenReturn("staff-1");
    when(copies.lockByBranchIdAndGameId(branchId, gameId))
        .thenReturn(List.of(damaged, busy, spare, broken));
    var rental = new Commitment(UUID.randomUUID(), now.minusSeconds(600), null);
    var hold = new Commitment(UUID.randomUUID(), now.plusSeconds(3600), now.plusSeconds(7200));
    UUID damagedId = damaged.getId();
    when(rentals.openCommitments(branchId, damagedId)).thenReturn(List.of(rental));
    when(holds.openCommitments(branchId, damagedId)).thenReturn(List.of(hold));
    // B is free of rentals but held elsewhere; only C is free everywhere.
    when(rentals.isFree(any(), any(), any(), any())).thenReturn(true);
    when(holds.isFree(any(), any(), any(), any())).thenReturn(true);
    UUID busyId = busy.getId();
    UUID brokenId = broken.getId();
    when(holds.isFree(eq(branchId), eq(busyId), any(), any())).thenReturn(false);

    var result =
        service.update(branchId, gameId, damaged.getId(), to(OUT_OF_SERVICE, " Missing pieces "));

    verify(rentals).move(rental, branchId, damaged.getId(), spare.getId());
    verify(holds).move(hold, branchId, damaged.getId(), spare.getId());
    verify(rentals, never()).isFree(eq(branchId), eq(brokenId), any(), any());
    assertThat(result.getState()).isEqualTo(RentalCopyState.OUT_OF_SERVICE);
    verify(changes)
        .save(
            argThat(
                change ->
                    change.getReason().equals("Missing pieces")
                        && change.getOperatorSubject().equals("staff-1")
                        && change.getFromStatus() == RentalCopyOperationalStatus.USABLE
                        && change.getToStatus() == RentalCopyOperationalStatus.OUT_OF_SERVICE));
  }

  @Test
  void conflictsAndChangesNothingWhenACommitmentCannotBeCovered() {
    var damaged = copy("A");
    var other = copy("B");
    when(access.requireOperator(branchId)).thenReturn("staff-1");
    when(copies.lockByBranchIdAndGameId(branchId, gameId)).thenReturn(List.of(damaged, other));
    UUID damagedId = damaged.getId();
    when(rentals.openCommitments(branchId, damagedId))
        .thenReturn(List.of(new Commitment(UUID.randomUUID(), now, null)));
    when(holds.isFree(any(), any(), any(), any())).thenReturn(true);

    assertThatThrownBy(() -> service.update(branchId, gameId, damaged.getId(), to(RETIRED, "Lost")))
        .isInstanceOfSatisfying(
            ResponseStatusException.class,
            failure -> assertThat(failure.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
    assertThat(damaged.isUsable()).isTrue();
    verify(rentals, never()).move(any(), any(), any(), any());
    verifyNoInteractions(changes);
  }

  @Test
  void requiresAReasonAndNeverReactivatesARetiredCopy() {
    var copy = copy("A");
    when(access.requireOperator(branchId)).thenReturn("staff-1");
    when(copies.lockByBranchIdAndGameId(branchId, gameId)).thenReturn(List.of(copy));

    assertThatThrownBy(() -> service.update(branchId, gameId, copy.getId(), to(RETIRED, null)))
        .isInstanceOf(IllegalArgumentException.class);
    service.update(branchId, gameId, copy.getId(), to(RETIRED, "Worn out"));
    assertThatThrownBy(() -> service.update(branchId, gameId, copy.getId(), to(USABLE, null)))
        .isInstanceOfSatisfying(
            ResponseStatusException.class,
            failure -> assertThat(failure.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
    // Repeating the current status is a no-op, not a new ledger entry.
    service.update(branchId, gameId, copy.getId(), to(RETIRED, "Again"));
    verify(changes, times(1)).save(any());
    assertThatThrownBy(() -> service.update(branchId, gameId, UUID.randomUUID(), to(USABLE, null)))
        .isInstanceOf(ResponseStatusException.class);
  }
}
