package com.chanakanlabs.theparlor.inventory.internal.domain;

import static org.junit.jupiter.api.Assertions.*;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class BranchEditionTests {
  @Test
  void stockMutationsPreserveHeldUnitsAndPrice() {
    var edition =
        BranchEdition.create(new BranchEditionId(UUID.randomUUID(), UUID.randomUUID()), 100);
    edition.adjustStock(5);
    edition.hold(3);
    assertThrows(IllegalArgumentException.class, () -> edition.adjustStock(-3));
    assertThrows(IllegalArgumentException.class, () -> edition.hold(3));
    edition.changePrice(200);
    edition.fulfill(2);
    edition.release(1);
    assertEquals(3, edition.getSaleOnHand());
    assertEquals(0, edition.getSaleHeld());
    assertEquals(200L, edition.getSalePriceMinor());
    assertThrows(IllegalArgumentException.class, () -> edition.fulfill(1));
    assertThrows(IllegalArgumentException.class, () -> edition.changePrice(-1));
  }

  @Test
  void overflowDoesNotChangeStock() {
    var edition =
        BranchEdition.create(new BranchEditionId(UUID.randomUUID(), UUID.randomUUID()), 0);
    edition.adjustStock(Integer.MAX_VALUE);
    assertThrows(ArithmeticException.class, () -> edition.adjustStock(1));
    assertEquals(Integer.MAX_VALUE, edition.getSaleOnHand());
  }
}
