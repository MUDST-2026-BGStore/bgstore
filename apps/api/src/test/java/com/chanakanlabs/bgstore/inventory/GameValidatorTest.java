package com.chanakanlabs.bgstore.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import com.chanakanlabs.bgstore.contract.model.BranchCopiesRequest;
import com.chanakanlabs.bgstore.contract.model.GameCategory;
import com.chanakanlabs.bgstore.contract.model.GameLifecycle;
import com.chanakanlabs.bgstore.contract.model.GameRequest;
import com.chanakanlabs.bgstore.web.FieldViolation;
import com.chanakanlabs.bgstore.web.ValidationFailedException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class GameValidatorTest {

  private static final UUID SILOM = UUID.randomUUID();
  private static final UUID THONGLOR = UUID.randomUUID();
  private static final Set<UUID> KNOWN_BRANCHES = Set.of(SILOM, THONGLOR);

  @Test
  void keepsTrimmedValuesAndDefaultsAnAbsentLifecycleToActive() {
    var request = request();
    request.setDescription("  Build routes across the map.  ");
    request.setDifficulty(" Easy to teach ");
    request.setPlayTimeMinutes(60);
    request.setCopies(List.of(new BranchCopiesRequest(SILOM, 2)));

    var command = GameValidator.validate(request, KNOWN_BRANCHES);

    assertThat(command.title()).isEqualTo("Ticket to Ride");
    assertThat(command.description()).isEqualTo("Build routes across the map.");
    assertThat(command.difficulty()).isEqualTo("Easy to teach");
    assertThat(command.playTimeMinutes()).isEqualTo(60);
    assertThat(command.lifecycle()).isEqualTo(GameLifecycle.ACTIVE);
    assertThat(command.copiesByBranch()).containsExactly(Map.entry(SILOM, 2));
  }

  @Test
  void collapsesBlankOptionalTextToNothingRatherThanEmptyStrings() {
    var request = request();
    request.setDescription("   ");
    request.setDifficulty("");

    var command = GameValidator.validate(request, KNOWN_BRANCHES);

    assertThat(command.description()).isNull();
    assertThat(command.difficulty()).isNull();
  }

  @Test
  void trimsTagsDropsBlanksAndKeepsTheFirstOfEachDuplicate() {
    var request = request();
    request.setTags(List.of(" beginner friendly ", "  ", "beginner friendly", "30–60 min"));

    var command = GameValidator.validate(request, KNOWN_BRANCHES);

    assertThat(command.tags()).containsExactly("beginner friendly", "30–60 min");
  }

  @Test
  void rejectsATitleThatIsOnlyWhitespace() {
    var request = request();
    request.setTitle("   ");

    assertThat(violationsOf(request))
        .containsExactly(new FieldViolation("title", FieldViolation.REQUIRED));
  }

  @Test
  void rejectsAPlayerRangeThatRunsBackwards() {
    var request = request();
    request.setMinPlayers(5);
    request.setMaxPlayers(2);

    assertThat(violationsOf(request))
        .containsExactly(new FieldViolation("maxPlayers", GameValidator.BELOW_MINIMUM));
  }

  @Test
  void acceptsASinglePlayerRange() {
    var request = request();
    request.setMinPlayers(4);
    request.setMaxPlayers(4);

    assertThat(GameValidator.validate(request, KNOWN_BRANCHES).maxPlayers()).isEqualTo(4);
  }

  @Test
  void rejectsCopiesForABranchTheDirectoryDoesNotHold() {
    var request = request();
    var unknown = UUID.randomUUID();
    request.setCopies(
        List.of(new BranchCopiesRequest(SILOM, 1), new BranchCopiesRequest(unknown, 1)));

    assertThat(violationsOf(request))
        .containsExactly(new FieldViolation("copies[1].branchId", GameValidator.UNKNOWN_BRANCH));
  }

  @Test
  void rejectsTheSameBranchTwiceRatherThanSilentlyKeepingOneCount() {
    var request = request();
    request.setCopies(
        List.of(new BranchCopiesRequest(SILOM, 1), new BranchCopiesRequest(SILOM, 4)));

    assertThat(violationsOf(request))
        .containsExactly(new FieldViolation("copies[1].branchId", GameValidator.DUPLICATE_BRANCH));
  }

  @Test
  void reportsEveryBrokenRuleAtOnceSoAFormCanMarkAllOfItsFields() {
    var request = request();
    request.setTitle(" ");
    request.setMinPlayers(6);
    request.setMaxPlayers(2);
    request.setCopies(List.of(new BranchCopiesRequest(UUID.randomUUID(), 1)));

    assertThat(violationsOf(request))
        .containsExactly(
            new FieldViolation("title", FieldViolation.REQUIRED),
            new FieldViolation("maxPlayers", GameValidator.BELOW_MINIMUM),
            new FieldViolation("copies[0].branchId", GameValidator.UNKNOWN_BRANCH));
  }

  private static List<FieldViolation> violationsOf(GameRequest request) {
    var thrown =
        catchThrowableOfType(
            ValidationFailedException.class, () -> GameValidator.validate(request, KNOWN_BRANCHES));
    assertThat(thrown).as("expected a ValidationFailedException").isNotNull();

    return thrown.violations();
  }

  private static GameRequest request() {
    return new GameRequest("  Ticket to Ride  ", GameCategory.FAMILY, 2, 5);
  }
}
