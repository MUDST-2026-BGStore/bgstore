package com.chanakanlabs.bgstore.inventory;

import com.chanakanlabs.bgstore.contract.model.BranchCopiesRequest;
import com.chanakanlabs.bgstore.contract.model.GameGuide;
import com.chanakanlabs.bgstore.contract.model.GameGuideStep;
import com.chanakanlabs.bgstore.contract.model.GameLifecycle;
import com.chanakanlabs.bgstore.contract.model.GameRequest;
import com.chanakanlabs.bgstore.contract.model.LocalizedDescription;
import com.chanakanlabs.bgstore.contract.model.LocalizedGuideText;
import com.chanakanlabs.bgstore.contract.model.LocalizedTitle;
import com.chanakanlabs.bgstore.web.FieldViolation;
import com.chanakanlabs.bgstore.web.ValidationFailedException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.lang.Nullable;

/**
 * Checks the rules the OpenAPI schema cannot state, and normalises what it accepts.
 *
 * <p>Ranges, lengths and required properties are already enforced by the bean-validation
 * annotations on the generated request model, so this only covers relationships between properties
 * and references to other records. Every violation is collected before failing so a form can mark
 * all of its bad fields in one pass.
 */
final class GameValidator {

  /** The player range runs the wrong way. */
  static final String BELOW_MINIMUM = "belowMinimum";

  /** The payload names a branch the directory does not hold. */
  static final String UNKNOWN_BRANCH = "unknownBranch";

  /** The payload sets copies for the same branch twice. */
  static final String DUPLICATE_BRANCH = "duplicateBranch";

  private GameValidator() {}

  static GameCommand validate(GameRequest request, Set<UUID> knownBranchIds) {
    var violations = new ArrayList<FieldViolation>();

    var title = titleOf(request.getTitle(), violations);

    int minPlayers = request.getMinPlayers();
    int maxPlayers = request.getMaxPlayers();
    if (maxPlayers < minPlayers) {
      violations.add(new FieldViolation("maxPlayers", BELOW_MINIMUM));
    }

    var copies = copiesByBranch(request, knownBranchIds, violations);
    var imageUrls = imageUrls(request, violations);
    var guide = guideOf(request.getGuide(), violations);

    if (!violations.isEmpty()) {
      throw new ValidationFailedException(violations);
    }

    return new GameCommand(
        title,
        descriptionOf(request.getDescription()),
        request.getCategory(),
        minPlayers,
        maxPlayers,
        request.getPlayTimeMinutes(),
        trimmed(request.getDifficulty()),
        tags(request),
        imageUrls,
        guide,
        Objects.requireNonNullElse(request.getLifecycle(), GameLifecycle.ACTIVE),
        copies);
  }

  /**
   * The catalogue's canonical entry is the English title, so a payload without one is missing a
   * required value. Thai rides alongside and stays optional: a blank Thai title is an absent
   * translation, not a rejected one.
   *
   * <p>The schema already requires {@code title.en} to be present and within its length, so this
   * only has to catch a title that is present but blank — which the schema's {@code minLength}
   * cannot see past the whitespace.
   */
  private static LocalizedText titleOf(
      @Nullable LocalizedTitle title, List<FieldViolation> violations) {
    var english = title == null ? null : trimmed(title.getEn());
    if (english == null) {
      violations.add(new FieldViolation("title.en", FieldViolation.REQUIRED));
    }

    return new LocalizedText(english, title == null ? null : trimmed(title.getTh()));
  }

  /** Both languages are optional here, so a description may carry neither. */
  private static LocalizedText descriptionOf(@Nullable LocalizedDescription description) {
    if (description == null) {
      return LocalizedText.NONE;
    }

    return new LocalizedText(trimmed(description.getEn()), trimmed(description.getTh()));
  }

  /**
   * Every part of a guide is optional, but a step that is present needs a title to be numbered
   * under, and like a game title its English is the canonical entry.
   */
  private static PlayGuide guideOf(@Nullable GameGuide guide, List<FieldViolation> violations) {
    if (guide == null) {
      return PlayGuide.EMPTY;
    }

    var requested = Objects.requireNonNullElse(guide.getSteps(), List.<GameGuideStep>of());
    var steps = new ArrayList<PlayGuide.Step>();
    for (int index = 0; index < requested.size(); index++) {
      var step = requested.get(index);
      // Bean validation passes a null element through, so it is caught here.
      if (step == null) {
        violations.add(new FieldViolation("guide.steps[" + index + "]", FieldViolation.REQUIRED));
        continue;
      }
      var title = step.getTitle();
      var english = title == null ? null : trimmed(title.getEn());
      if (english == null) {
        violations.add(
            new FieldViolation("guide.steps[" + index + "].title.en", FieldViolation.REQUIRED));
        continue;
      }

      steps.add(
          new PlayGuide.Step(
              new LocalizedText(english, trimmed(title.getTh())), guideTextOf(step.getBody())));
    }

    return new PlayGuide(
        guideTextOf(guide.getGoal()),
        guideTextOf(guide.getPlayers()),
        guideTextOf(guide.getEquipment()),
        List.copyOf(steps));
  }

  private static LocalizedText guideTextOf(@Nullable LocalizedGuideText text) {
    if (text == null) {
      return LocalizedText.NONE;
    }

    return new LocalizedText(trimmed(text.getEn()), trimmed(text.getTh()));
  }

  /**
   * De-duplicated, order kept. The schema's pattern already rejects blanks and whitespace, so what
   * reaches here is an address the browser can load as it is — except a null entry, which bean
   * validation lets through.
   */
  private static List<String> imageUrls(GameRequest request, List<FieldViolation> violations) {
    var requested = Objects.requireNonNullElse(request.getImageUrls(), List.<String>of());
    var urls = new LinkedHashSet<String>();
    for (int index = 0; index < requested.size(); index++) {
      var url = requested.get(index);
      if (url == null) {
        violations.add(new FieldViolation("imageUrls[" + index + "]", FieldViolation.REQUIRED));
      } else {
        urls.add(url);
      }
    }

    return List.copyOf(urls);
  }

  private static Map<UUID, Integer> copiesByBranch(
      GameRequest request, Set<UUID> knownBranchIds, List<FieldViolation> violations) {
    var requested = Objects.requireNonNullElse(request.getCopies(), List.<BranchCopiesRequest>of());
    var copies = new LinkedHashMap<UUID, Integer>();

    for (int index = 0; index < requested.size(); index++) {
      var entry = requested.get(index);
      var branchId = entry.getBranchId();
      var field = "copies[" + index + "].branchId";

      if (!knownBranchIds.contains(branchId)) {
        violations.add(new FieldViolation(field, UNKNOWN_BRANCH));
      } else if (copies.putIfAbsent(branchId, entry.getCopies()) != null) {
        violations.add(new FieldViolation(field, DUPLICATE_BRANCH));
      }
    }

    return copies;
  }

  /** Trimmed, de-duplicated, blank entries dropped, order kept. */
  private static List<String> tags(GameRequest request) {
    var tags = new LinkedHashSet<String>();
    for (var tag : Objects.requireNonNullElse(request.getTags(), List.<String>of())) {
      var trimmed = trimmed(tag);
      if (trimmed != null) {
        tags.add(trimmed);
      }
    }

    return List.copyOf(tags);
  }

  /** Whitespace-only text carries no more meaning than an absent value, so both become null. */
  private static @Nullable String trimmed(@Nullable String value) {
    if (value == null) {
      return null;
    }
    var trimmed = value.strip();

    return trimmed.isEmpty() ? null : trimmed;
  }
}
