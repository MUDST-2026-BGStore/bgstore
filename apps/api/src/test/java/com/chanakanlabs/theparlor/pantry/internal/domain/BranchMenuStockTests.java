package com.chanakanlabs.theparlor.pantry.internal.domain;

import static org.assertj.core.api.Assertions.*;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class BranchMenuStockTests {
  BranchMenuStock stocked(int units) {
    var stock = BranchMenuStock.empty(new BranchMenuStockId(UUID.randomUUID(), UUID.randomUUID()));
    stock.adjust(units);
    return stock;
  }

  @Test
  void holdsOnlyWhatIsAvailableAndAllOrNothing() {
    var stock = stocked(5);
    stock.hold(3);
    assertThat(stock.available()).isEqualTo(2);
    assertThatIllegalArgumentException().isThrownBy(() -> stock.hold(3));
    assertThat(stock.getHeld()).isEqualTo(3);
  }

  @Test
  void releaseReturnsUnitsAndFulfilConsumesThem() {
    var stock = stocked(5);
    stock.hold(4);
    stock.release(1);
    assertThat(stock.getHeld()).isEqualTo(3);
    stock.fulfill(3);
    assertThat(stock.getOnHand()).isEqualTo(2);
    assertThat(stock.getHeld()).isZero();
    assertThatIllegalArgumentException().isThrownBy(() -> stock.release(1));
  }

  @Test
  void correctionsCannotTakeHeldUnits() {
    var stock = stocked(4);
    stock.hold(3);
    assertThatIllegalArgumentException().isThrownBy(() -> stock.adjust(-2));
    stock.adjust(-1);
    assertThat(stock.available()).isZero();
    assertThatIllegalArgumentException().isThrownBy(() -> stock.adjust(0));
  }

  @Test
  void rejectsNonPositiveQuantities() {
    var stock = stocked(2);
    assertThatIllegalArgumentException().isThrownBy(() -> stock.hold(0));
    assertThatIllegalArgumentException().isThrownBy(() -> stock.fulfill(-1));
  }
}
