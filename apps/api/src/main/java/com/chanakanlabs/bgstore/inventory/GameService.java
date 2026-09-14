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
import java.util.LinkedHashMap;
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
    var selectedBranch = branchId == null ? null : branches.findById(branchId).orElse(null);
    if (branchId != null && selectedBranch == null) {
      throw new ValidationFailedException(
          List.of(new FieldViolation("branchId", GameValidator.UNKNOWN_BRANCH)));
    }
    if (selectedBranch != null) {
      if (accessPolicy.hasStaffAccess()) {
        accessPolicy.requireBranch(selectedBranch.id());
      }
    } else if (accessPolicy.hasStaffAccess() && !accessPolicy.hasManagerAccess()) {
      accessPolicy.requireAnyAssignedBranch();
      throw new ValidationFailedException(
          List.of(new FieldViolation("branchId", "requiredForStaff")));
    }

    var branchNames =
        branches.findAll().stream().collect(Collectors.toMap(Branch::id, Branch::name));

    return GameResponses.toListResponse(games.findPage(filter), filter, branchNames);
  }

  @Transactional(readOnly = true)
  GameDetail get(UUID id) {
    if (accessPolicy.hasStaffAccess()) accessPolicy.requireAnyAssignedBranch();
    return detailOf(games.findById(id).orElseThrow(() -> new ResourceNotFoundException(GAME, id)));
  }

  GameDetail create(GameRequest request) {
    accessPolicy.requireStaffOrManager();
    var command = GameValidator.validate(request, branchIds());
    requireWritableBranches(command.copiesByBranch());

    var id = games.insert(command);
    games.replaceStock(id, command.copiesByBranch());

    return get(id);
  }

  GameDetail update(UUID id, GameRequest request) {
    accessPolicy.requireStaffOrManager();
    var command = GameValidator.validate(request, branchIds());
    requireWritableBranches(command.copiesByBranch());
    command = preserveUnassignedStock(id, command);
    rejectRemovingCopiesInUse(id, command);

    if (!games.update(id, command)) {
      throw new ResourceNotFoundException(GAME, id);
    }
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
    var visibleBranches =
        branches.findAll().stream()
            .filter(branch -> accessPolicy.canAccessBranch(branch.id()))
            .toList();
    var visibleIds = visibleBranches.stream().map(Branch::id).collect(Collectors.toSet());
    var visibleStock =
        games.findStock(game.id()).stream()
            .filter(row -> visibleIds.contains(row.branchId()))
            .toList();
    return GameResponses.toDetail(game, visibleStock, visibleBranches);
  }

  private Set<UUID> branchIds() {
    return branches.findAll().stream().map(Branch::id).collect(Collectors.toSet());
  }

  private void requireWritableBranches(java.util.Map<UUID, Integer> copies) {
    if (!accessPolicy.hasStaffAccess()) return;
    copies
        .keySet()
        .forEach(
            id -> {
              var branch = branches.findById(id).orElseThrow();
              accessPolicy.requireBranch(branch.id());
            });
  }

  private GameCommand preserveUnassignedStock(UUID id, GameCommand command) {
    if (!accessPolicy.hasStaffAccess() || accessPolicy.hasManagerAccess()) return command;
    var merged = new LinkedHashMap<>(command.copiesByBranch());
    games.findStock(id).forEach(row -> merged.putIfAbsent(row.branchId(), row.copies()));
    return new GameCommand(
        command.title(),
        command.description(),
        command.category(),
        command.minPlayers(),
        command.maxPlayers(),
        command.playTimeMinutes(),
        command.difficulty(),
        command.tags(),
        command.imageUrls(),
        command.guide(),
        command.lifecycle(),
        merged);
  }
}
