package com.chanakanlabs.bgstore.inventory;

import com.chanakanlabs.bgstore.contract.model.BranchCopiesRequest;
import com.chanakanlabs.bgstore.contract.model.GameLifecycle;
import com.chanakanlabs.bgstore.contract.model.GameRequest;
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

    var title = trimmed(request.getTitle());
    if (title == null) {
      violations.add(new FieldViolation("title", FieldViolation.REQUIRED));
    }

    int minPlayers = request.getMinPlayers();
    int maxPlayers = request.getMaxPlayers();
    if (maxPlayers < minPlayers) {
      violations.add(new FieldViolation("maxPlayers", BELOW_MINIMUM));
    }

    var copies = copiesByBranch(request, knownBranchIds, violations);

    if (!violations.isEmpty()) {
      throw new ValidationFailedException(violations);
    }

    return new GameCommand(
        Objects.requireNonNull(title),
        trimmed(request.getDescription()),
        request.getCategory(),
        minPlayers,
        maxPlayers,
        request.getPlayTimeMinutes(),
        trimmed(request.getDifficulty()),
        tags(request),
        Objects.requireNonNullElse(request.getLifecycle(), GameLifecycle.ACTIVE),
        copies);
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
