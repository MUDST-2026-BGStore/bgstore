package com.chanakanlabs.theparlor.inventory.internal.application;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.chanakanlabs.theparlor.branches.BranchAccess;
import com.chanakanlabs.theparlor.catalog.CatalogLookup;
import com.chanakanlabs.theparlor.contracts.model.Game;
import com.chanakanlabs.theparlor.contracts.model.GameEdition;
import com.chanakanlabs.theparlor.inventory.SaleInventory.Selection;
import com.chanakanlabs.theparlor.inventory.internal.domain.*;
import com.chanakanlabs.theparlor.inventory.internal.persistence.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class SaleInventoryServiceTests {
  @Test
  void holdsCurrentPriceAndReleasesOrFulfillsStock() {
    var branch = UUID.randomUUID();
    var gameId = UUID.randomUUID();
    var editionId = UUID.randomUUID();
    var catalog = mock(CatalogLookup.class);
    var stock = mock(BranchEditionRepository.class);
    var offerings = mock(BranchGameRepository.class);
    var service = new SaleInventoryService(stock, offerings, catalog, mock(BranchAccess.class));
    var edition = mock(GameEdition.class);
    var game = mock(Game.class);
    when(edition.getActive()).thenReturn(true);
    when(edition.getGameId()).thenReturn(gameId);
    when(game.getName()).thenReturn("Game");
    when(catalog.edition(editionId)).thenReturn(Optional.of(edition));
    when(catalog.activeGame(gameId)).thenReturn(Optional.of(game));
    var offering = BranchGame.create(new BranchGameId(branch, gameId));
    offering.configure(true, false, false, 0);
    when(offerings.lockById(offering.getId())).thenReturn(Optional.of(offering));
    var row = BranchEdition.create(new BranchEditionId(branch, editionId), 10000);
    row.adjustStock(5);
    when(stock.lockById(row.getId())).thenReturn(Optional.of(row));
    var items = List.of(new Selection(editionId, 2));
    assertThat(service.hold(branch, items))
        .singleElement()
        .satisfies(item -> assertThat(item.unitPriceMinor()).isEqualTo(10000));
    assertThat(row.getSaleHeld()).isEqualTo(2);
    service.fulfill(branch, List.of(new Selection(editionId, 1)));
    service.release(branch, List.of(new Selection(editionId, 1)));
    assertThat(row.getSaleOnHand()).isEqualTo(4);
    assertThat(row.getSaleHeld()).isZero();
    assertThatThrownBy(() -> service.hold(branch, List.of(new Selection(editionId, 5))))
        .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
    assertThatThrownBy(
            () ->
                service.hold(
                    branch, List.of(new Selection(editionId, 1), new Selection(editionId, 1))))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
