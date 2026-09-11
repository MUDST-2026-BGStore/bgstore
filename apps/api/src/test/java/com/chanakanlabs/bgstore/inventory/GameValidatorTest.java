package com.chanakanlabs.bgstore.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import com.chanakanlabs.bgstore.contract.model.BranchCopiesRequest;
import com.chanakanlabs.bgstore.contract.model.GameCategory;
import com.chanakanlabs.bgstore.contract.model.GameLifecycle;
import com.chanakanlabs.bgstore.contract.model.GameRequest;
import com.chanakanlabs.bgstore.contract.model.LocalizedDescription;
import com.chanakanlabs.bgstore.contract.model.LocalizedTitle;
import com.chanakanlabs.bgstore.web.FieldViolation;
import com.chanakanlabs.bgstore.web.ValidationFailedException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.lang.Nullable;

class GameValidatorTest {

  private static final UUID SILOM = UUID.randomUUID();
  private static final UUID THONGLOR = UUID.randomUUID();
  private static final Set<UUID> KNOWN_BRANCHES = Set.of(SILOM, THONGLOR);

  @Test
  void keepsTrimmedValuesAndDefaultsAnAbsentLifecycleToActive() {
    var request = request();
    request.setDescription(description("  Build routes across the map.  ", null));
    request.setDifficulty(" Easy to teach ");
    request.setPlayTimeMinutes(60);
    request.setCopies(List.of(new BranchCopiesRequest(SILOM, 2)));

    var command = GameValidator.validate(request, KNOWN_BRANCHES);

    assertThat(command.title().english()).isEqualTo("Ticket to Ride");
    assertThat(command.description().english()).isEqualTo("Build routes across the map.");
    assertThat(command.difficulty()).isEqualTo("Easy to teach");
    assertThat(command.playTimeMinutes()).isEqualTo(60);
    assertThat(command.lifecycle()).isEqualTo(GameLifecycle.ACTIVE);
    assertThat(command.copiesByBranch()).containsExactly(Map.entry(SILOM, 2));
  }

  @Test
  void collapsesBlankOptionalTextToNothingRatherThanEmptyStrings() {
    var request = request();
    request.setTitle(title("Ticket to Ride", "   "));
    request.setDescription(description("   ", ""));
    request.setDifficulty("");

    var command = GameValidator.validate(request, KNOWN_BRANCHES);

    // A blank translation is an absent one, in either language of either field.
    assertThat(command.title().thai()).isNull();
    assertThat(command.description().isEmpty()).isTrue();
    assertThat(command.difficulty()).isNull();
  }

  @Test
  void keepsBothLanguagesOfATitleAndDescription() {
    var request = request();
    request.setTitle(title(" Ticket to Ride ", "  ตั๋วรถไฟ  "));
    request.setDescription(description(" Build routes. ", " สร้างเส้นทาง "));

    var command = GameValidator.validate(request, KNOWN_BRANCHES);

    assertThat(command.title().english()).isEqualTo("Ticket to Ride");
    assertThat(command.title().thai()).isEqualTo("ตั๋วรถไฟ");
    assertThat(command.description().english()).isEqualTo("Build routes.");
    assertThat(command.description().thai()).isEqualTo("สร้างเส้นทาง");
  }

  @Test
  void acceptsAGameThatIsOnlyTranslatedIntoThai() {
    var request = request();
    request.setTitle(title("Ticket to Ride", "ตั๋วรถไฟ"));
    request.setDescription(description(null, "สร้างเส้นทาง"));

    var command = GameValidator.validate(request, KNOWN_BRANCHES);

    // Only the English title is required; an English description is not, so a
    // Thai-only description is stored as it was entered.
    assertThat(command.description().english()).isNull();
    assertThat(command.description().thai()).isEqualTo("สร้างเส้นทาง");
  }

  @Test
  void rejectsATitleThatCarriesOnlyAThaiTranslation() {
    var request = request();
    request.setTitle(title("   ", "ตั๋วรถไฟ"));

    // English is the entry every game carries, so a Thai-only title is missing
    // the required half rather than being a complete title.
    assertThat(violationsOf(request))
        .containsExactly(new FieldViolation("title.en", FieldViolation.REQUIRED));
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
    request.setTitle(title("   ", null));

    assertThat(violationsOf(request))
        .containsExactly(new FieldViolation("title.en", FieldViolation.REQUIRED));
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
    request.setTitle(title(" ", null));
    request.setMinPlayers(6);
    request.setMaxPlayers(2);
    request.setCopies(List.of(new BranchCopiesRequest(UUID.randomUUID(), 1)));

    assertThat(violationsOf(request))
        .containsExactly(
            new FieldViolation("title.en", FieldViolation.REQUIRED),
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
    return new GameRequest(title("  Ticket to Ride  ", null), GameCategory.FAMILY, 2, 5);
  }

  private static LocalizedTitle title(String english, @Nullable String thai) {
    var title = new LocalizedTitle(english);
    title.setTh(thai);

    return title;
  }

  private static LocalizedDescription description(@Nullable String english, @Nullable String thai) {
    var description = new LocalizedDescription();
    description.setEn(english);
    description.setTh(thai);

    return description;
  }
}
