package com.chanakanlabs.theparlor.inventory.internal.application;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.*;

import com.chanakanlabs.theparlor.branches.BranchAccess;
import com.chanakanlabs.theparlor.catalog.CatalogLookup;
import com.chanakanlabs.theparlor.contracts.model.Game;
import com.chanakanlabs.theparlor.inventory.internal.domain.BranchGame;
import com.chanakanlabs.theparlor.inventory.internal.domain.BranchGameId;
import com.chanakanlabs.theparlor.inventory.internal.domain.RentalCopy;
import com.chanakanlabs.theparlor.inventory.internal.domain.RentalCopyOperationalStatus;
import com.chanakanlabs.theparlor.inventory.internal.persistence.*;
import com.chanakanlabs.theparlor.inventory.spi.CopyCommitments;
import com.chanakanlabs.theparlor.inventory.spi.CopyCommitments.Use;
import java.time.Clock;
import java.util.*;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Limit;

class InventoryServiceTests {
  final UUID branchId = UUID.randomUUID();
  final CatalogLookup catalog = mock(CatalogLookup.class);
  final BranchGameRepository offerings = mock(BranchGameRepository.class);
  final BranchEditionRepository stock = mock(BranchEditionRepository.class);
  final RentalCopyRepository copies = mock(RentalCopyRepository.class);
  final CopyCommitments commitments = mock(CopyCommitments.class);
  final InventoryService service =
      new InventoryService(
          mock(BranchAccess.class),
          catalog,
          offerings,
          stock,
          mock(SaleStockAdjustmentRepository.class),
          copies,
          new CopyUses(List.of(commitments), Clock.systemUTC()));

  static UUID id(int n) {
    return new UUID(0, n);
  }

  BranchGame offering(int n, boolean rental) {
    var offering = BranchGame.create(new BranchGameId(branchId, id(n)));
    offering.configure(true, rental, false, 4000);
    return offering;
  }

  @Test
  void scansOfferingsInChunksUntilTheFilteredPageIsFull() {
    // 120 offerings; the catalog reports only every 40th as active and matching.
    var all = IntStream.rangeClosed(1, 120).mapToObj(n -> offering(n, true)).toList();
    when(offerings.firstOfBranch(eq(branchId), any()))
        .thenAnswer(call -> all.stream().limit(call.<Limit>getArgument(1).max()).toList());
    when(offerings.nextOfBranch(eq(branchId), any(), any()))
        .thenAnswer(
            call ->
                all.stream()
                    .filter(o -> o.getId().gameId().compareTo(call.<UUID>getArgument(1)) > 0)
                    .limit(call.<Limit>getArgument(2).max())
                    .toList());
    when(catalog.activeGames(anyCollection(), eq("cat")))
        .thenAnswer(
            call ->
                call.<Collection<UUID>>getArgument(0).stream()
                    .filter(game -> game.getLeastSignificantBits() % 40 == 0)
                    .map(game -> new Game().id(game).name("Cat " + game))
                    .toList());

    var first = service.listBranchGames(branchId, 2, null, "  CAT ");
    assertThat(first.getItems())
        .extracting(item -> item.getGame().getId())
        .containsExactly(id(40), id(80));
    assertThat(first.getNextCursor()).isNotNull();
    var second = service.listBranchGames(branchId, 2, first.getNextCursor(), "cat");
    assertThat(second.getItems())
        .extracting(item -> item.getGame().getId())
        .containsExactly(id(120));
    assertThat(second.getNextCursor()).isNull();
    // A cursor is bound to its search text.
    assertThatThrownBy(() -> service.listBranchGames(branchId, 2, first.getNextCursor(), "dog"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void countsRentalCopiesByConditionAndCurrentCommitments() {
    var game = new Game().id(id(1)).name("Game");
    when(catalog.activeGame(id(1))).thenReturn(Optional.of(game));
    var copiesOfGame = new ArrayList<RentalCopy>();
    for (String code : List.of("free", "in-use", "held", "broken", "retired")) {
      var copy = spy(RentalCopy.register(branchId, id(1), code));
      doReturn(UUID.nameUUIDFromBytes(code.getBytes())).when(copy).getId();
      copiesOfGame.add(copy);
    }
    copiesOfGame.get(3).changeCondition(RentalCopyOperationalStatus.OUT_OF_SERVICE);
    copiesOfGame.get(4).changeCondition(RentalCopyOperationalStatus.RETIRED);
    when(copies.findByBranchIdAndGameIdIn(eq(branchId), anyCollection())).thenReturn(copiesOfGame);
    var uses =
        Map.of(copiesOfGame.get(1).getId(), Use.IN_USE, copiesOfGame.get(2).getId(), Use.RESERVED);
    when(commitments.usesAt(eq(branchId), any())).thenReturn(uses);

    when(offerings.findById(new BranchGameId(branchId, id(1))))
        .thenReturn(Optional.of(offering(1, true)));
    var offered = service.getBranchGame(branchId, id(1));
    assertThat(offered.getRentalTotal()).isEqualTo(4);
    assertThat(offered.getRentalUsable()).isEqualTo(3);
    assertThat(offered.getRentalAvailableNow()).isEqualTo(1);
    assertThat(offered.getEditions()).isEmpty();

    when(offerings.findById(new BranchGameId(branchId, id(1))))
        .thenReturn(Optional.of(offering(1, false)));
    assertThat(service.getBranchGame(branchId, id(1)).getRentalAvailableNow()).isZero();
  }
}
