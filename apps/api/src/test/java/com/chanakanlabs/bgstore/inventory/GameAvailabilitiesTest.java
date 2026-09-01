package com.chanakanlabs.bgstore.inventory;

import static org.assertj.core.api.Assertions.assertThat;

import com.chanakanlabs.bgstore.contract.model.GameAvailability;
import com.chanakanlabs.bgstore.contract.model.GameLifecycle;
import org.junit.jupiter.api.Test;

class GameAvailabilitiesTest {

  @Test
  void reportsAvailableWhileAtLeastOneCopyIsFree() {
    assertThat(GameAvailabilities.of(GameLifecycle.ACTIVE, 3, 1))
        .isEqualTo(GameAvailability.AVAILABLE);
  }

  @Test
  void reportsAllCopiesOutWhenEveryCopyIsOnASession() {
    assertThat(GameAvailabilities.of(GameLifecycle.ACTIVE, 2, 0))
        .isEqualTo(GameAvailability.ALL_COPIES_OUT);
  }

  @Test
  void reportsNotStockedWhenTheBranchHoldsNoCopies() {
    assertThat(GameAvailabilities.of(GameLifecycle.ACTIVE, 0, 0))
        .isEqualTo(GameAvailability.NOT_STOCKED);
  }

  @Test
  void reportsRetiredWhateverTheShelvesHold() {
    assertThat(GameAvailabilities.of(GameLifecycle.RETIRED, 3, 3))
        .isEqualTo(GameAvailability.RETIRED);
    assertThat(GameAvailabilities.of(GameLifecycle.RETIRED, 0, 0))
        .isEqualTo(GameAvailability.RETIRED);
  }
}
