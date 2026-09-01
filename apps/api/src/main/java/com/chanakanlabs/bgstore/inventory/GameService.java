package com.chanakanlabs.bgstore.inventory;

import com.chanakanlabs.bgstore.branches.Branch;
import com.chanakanlabs.bgstore.branches.BranchDirectory;
import com.chanakanlabs.bgstore.contract.model.GameDetail;
import com.chanakanlabs.bgstore.contract.model.GameListResponse;
import com.chanakanlabs.bgstore.contract.model.GameRequest;
import com.chanakanlabs.bgstore.identity.AccessPolicy;
import com.chanakanlabs.bgstore.web.FieldViolation;
import com.chanakanlabs.bgstore.web.ResourceNotFoundException;
import com.chanakanlabs.bgstore.web.ValidationFailedException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The game catalogue's behaviour: what the controller is a thin adapter over. */
@Service
@Transactional
class GameService {

  /** Copies a session is holding cannot be taken off the shelves. */
  static final String BELOW_IN_USE = "belowInUse";

  private static final String GAME = "Game";

  private final GameRepository games;
  private final BranchDirectory branches;
  private final AccessPolicy accessPolicy;

  GameService(GameRepository games, BranchDirectory branches, AccessPolicy accessPolicy) {
    this.games = games;
    this.branches = branches;
    this.accessPolicy = accessPolicy;
  }

  @Transactional(readOnly = true)
  GameListResponse list(GameFilter filter) {
    var branchId = filter.branchId();
    if (branchId != null && branches.findById(branchId).isEmpty()) {
      throw new ValidationFailedException(
          List.of(new FieldViolation("branchId", GameValidator.UNKNOWN_BRANCH)));
    }

    var branchNames =
        branches.findAll().stream().collect(Collectors.toMap(Branch::id, Branch::name));

    return GameResponses.toListResponse(games.findPage(filter), filter, branchNames);
  }

  @Transactional(readOnly = true)
  GameDetail get(UUID id) {
    return detailOf(games.findById(id).orElseThrow(() -> new ResourceNotFoundException(GAME, id)));
  }

  GameDetail create(GameRequest request) {
    accessPolicy.requireStaffOrManager();
    var command = GameValidator.validate(request, branchIds());

    var id = games.insert(command);
    games.replaceStock(id, command.copiesByBranch());

    return get(id);
  }

  GameDetail update(UUID id, GameRequest request) {
    accessPolicy.requireStaffOrManager();
    var command = GameValidator.validate(request, branchIds());

    if (games.findById(id).isEmpty()) {
      throw new ResourceNotFoundException(GAME, id);
    }
    rejectRemovingCopiesInUse(id, command);

    games.update(id, command);
    games.replaceStock(id, command.copiesByBranch());

    return get(id);
  }

  void retire(UUID id) {
    accessPolicy.requireStaffOrManager();
    if (!games.retire(id)) {
      throw new ResourceNotFoundException(GAME, id);
    }
  }

  /**
   * Guards invariant 5 in {@code docs/domain-model.md}: a copy assigned to an active play session
   * stays on the books. Nothing assigns copies yet, so this only bites once play sessions exist.
   */
  private void rejectRemovingCopiesInUse(UUID id, GameCommand command) {
    var inUseByBranch =
        games.findStock(id).stream()
            .filter(row -> row.inUse() > 0)
            .collect(Collectors.toMap(BranchStockRow::branchId, BranchStockRow::inUse));
    if (inUseByBranch.isEmpty()) {
      return;
    }

    var violations = new ArrayList<FieldViolation>();
    var index = 0;
    for (var requested : command.copiesByBranch().entrySet()) {
      if (requested.getValue() < inUseByBranch.getOrDefault(requested.getKey(), 0)) {
        violations.add(new FieldViolation("copies[" + index + "].copies", BELOW_IN_USE));
      }
      index++;
    }

    // Leaving a branch out of the payload clears it, which is equally not allowed
    // while that branch has copies out. There is no index to point at, so the
    // violation names the collection.
    var dropped = new LinkedHashSet<>(inUseByBranch.keySet());
    dropped.removeAll(command.copiesByBranch().keySet());
    if (!dropped.isEmpty()) {
      violations.add(new FieldViolation("copies", BELOW_IN_USE));
    }

    if (!violations.isEmpty()) {
      throw new ValidationFailedException(violations);
    }
  }

  private GameDetail detailOf(StoredGame game) {
    return GameResponses.toDetail(game, games.findStock(game.id()), branches.findAll());
  }

  private Set<UUID> branchIds() {
    return branches.findAll().stream().map(Branch::id).collect(Collectors.toSet());
  }
}
