package com.chanakanlabs.theparlor.pantry.internal.application;

import static org.assertj.core.api.Assertions.*;

import com.chanakanlabs.theparlor.pantry.Pantry.Selection;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PantryServiceTests {
  @Test
  void sortsSelectionsSoRowsAreAlwaysLockedInOneOrder() {
    var low = new UUID(0, 1);
    var high = new UUID(0, 2);
    assertThat(PantryService.validate(List.of(new Selection(high, 1), new Selection(low, 2))))
        .extracting(Selection::menuItemId)
        .containsExactly(low, high);
  }

  @Test
  void rejectsEmptyDuplicateAndOutOfRangeSelections() {
    var id = UUID.randomUUID();
    assertThatIllegalArgumentException().isThrownBy(() -> PantryService.validate(List.of()));
    assertThatIllegalArgumentException()
        .isThrownBy(
            () -> PantryService.validate(List.of(new Selection(id, 1), new Selection(id, 2))));
    assertThatIllegalArgumentException()
        .isThrownBy(() -> PantryService.validate(List.of(new Selection(id, 0))));
    assertThatIllegalArgumentException()
        .isThrownBy(
            () ->
                PantryService.validate(List.of(new Selection(id, PantryService.MAX_QUANTITY + 1))));
  }
}
