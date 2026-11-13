package com.chanakanlabs.theparlor.inventory.internal.application;

import com.chanakanlabs.theparlor.branches.BranchAccess;
import com.chanakanlabs.theparlor.catalog.CatalogLookup;
import com.chanakanlabs.theparlor.contracts.model.CreateRentalCopyRequest;
import com.chanakanlabs.theparlor.contracts.model.RentalCopy;
import com.chanakanlabs.theparlor.contracts.model.RentalCopyPage;
import com.chanakanlabs.theparlor.contracts.model.UpdateRentalCopyRequest;
import com.chanakanlabs.theparlor.inventory.internal.domain.BranchGameId;
import com.chanakanlabs.theparlor.inventory.internal.domain.RentalCopyConditionChange;
import com.chanakanlabs.theparlor.inventory.internal.domain.RentalCopyOperationalStatus;
import com.chanakanlabs.theparlor.inventory.internal.persistence.BranchGameRepository;
import com.chanakanlabs.theparlor.inventory.internal.persistence.RentalCopyConditionChangeRepository;
import com.chanakanlabs.theparlor.inventory.internal.persistence.RentalCopyRepository;
import com.chanakanlabs.theparlor.inventory.spi.CopyCommitments;
import com.chanakanlabs.theparlor.inventory.spi.CopyCommitments.Commitment;
import com.chanakanlabs.theparlor.shared.KeysetCursor;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Individually tracked rental copies. Every operation requires branch-operator access. */
@Service
@Transactional(readOnly = true)
public class RentalCopiesService {
  private static final String LIST = "rental-copies";

  private final BranchAccess branches;
  private final CatalogLookup catalog;
  private final BranchGameRepository offerings;
  private final RentalCopyRepository copies;
  private final RentalCopyConditionChangeRepository conditionChanges;
  private final List<CopyCommitments> commitments;
  private final CopyUses copyUses;

  public RentalCopiesService(
      BranchAccess branches,
      CatalogLookup catalog,
      BranchGameRepository offerings,
      RentalCopyRepository copies,
      RentalCopyConditionChangeRepository conditionChanges,
      List<CopyCommitments> commitments,
      CopyUses copyUses) {
    this.branches = branches;
    this.catalog = catalog;
    this.offerings = offerings;
    this.copies = copies;
    this.conditionChanges = conditionChanges;
    this.commitments = commitments;
    this.copyUses = copyUses;
  }

  public RentalCopyPage list(
      UUID branchId, UUID gameId, Integer requestedLimit, @Nullable String cursor) {
    branches.requireOperator(branchId);
    if (!offerings.existsById(new BranchGameId(branchId, gameId))) throw missing();
    int limit = KeysetCursor.limit(requestedLimit);
    String filters = branchId + "|" + gameId;
    UUID after = KeysetCursor.afterId(cursor, LIST, filters);
    Specification<com.chanakanlabs.theparlor.inventory.internal.domain.RentalCopy> ofGame =
        (root, query, cb) ->
            cb.and(
                cb.equal(root.get("branchId"), branchId),
                cb.equal(root.get("gameId"), gameId),
                after == null ? cb.conjunction() : cb.greaterThan(root.get("id"), after));
    var rows = copies.findBy(ofGame, query -> query.sortBy(Sort.by("id")).limit(limit + 1).all());
    var page = rows.stream().limit(limit).toList();
    var uses = copyUses.now(branchId);
    var reasons = latestReasons(page.stream().map(copy -> copy.getId()).toList());
    var result =
        new RentalCopyPage(
            page.stream()
                .map(
                    copy ->
                        InventoryMapper.copy(
                            copy, uses.get(copy.getId()), reasons.get(copy.getId())))
                .toList());
    if (rows.size() > limit)
      result.setNextCursor(KeysetCursor.next(LIST, filters, page.getLast().getId()));
    return result;
  }

  @Transactional
  public RentalCopy create(UUID branchId, UUID gameId, CreateRentalCopyRequest request) {
    branches.requireOperator(branchId);
    if (catalog.activeGame(gameId).isEmpty()) throw missing();
    offerings
        .lockById(new BranchGameId(branchId, gameId))
        .orElseThrow(RentalCopiesService::missing);
    var copy =
        com.chanakanlabs.theparlor.inventory.internal.domain.RentalCopy.register(
            branchId, gameId, request.getAssetCode());
    try {
      copies.saveAndFlush(copy);
    } catch (DataIntegrityViolationException ex) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "Asset code already exists at this branch", ex);
    }
    return InventoryMapper.copy(copy, null, null);
  }

  /**
   * Changes a copy's physical condition. A copy leaving the rentable pool hands each of its open
   * commitments to another usable copy of the game that is free for that commitment's interval; if
   * any commitment cannot be covered, nothing changes and the request conflicts.
   */
  @Transactional
  public RentalCopy update(
      UUID branchId, UUID gameId, UUID copyId, UpdateRentalCopyRequest request) {
    String actor = branches.requireOperator(branchId);
    // Lock every copy of the game so no booking, check-in or other condition change can commit
    // or retire a replacement candidate while commitments are being moved.
    var siblings = copies.lockByBranchIdAndGameId(branchId, gameId);
    var copy =
        siblings.stream()
            .filter(candidate -> candidate.getId().equals(copyId))
            .findFirst()
            .orElseThrow(RentalCopiesService::missing);
    var status = RentalCopyOperationalStatus.valueOf(request.getOperationalStatus().name());

    if (status != copy.getOperationalStatus()) {
      if (copy.getOperationalStatus() == RentalCopyOperationalStatus.RETIRED)
        throw new ResponseStatusException(
            HttpStatus.CONFLICT, "A retired copy cannot be reactivated");
      var change = RentalCopyConditionChange.record(copy, status, request.getReason(), actor);
      if (copy.isUsable())
        moveCommitments(
            branchId,
            copyId,
            siblings.stream()
                .filter(candidate -> candidate.isUsable() && !candidate.getId().equals(copyId))
                .map(candidate -> candidate.getId())
                .toList());
      copy.changeCondition(status);
      conditionChanges.save(change);
      copies.flush();
    }
    return InventoryMapper.copy(
        copy, copyUses.now(branchId).get(copyId), latestReasons(List.of(copyId)).get(copyId));
  }

  /**
   * A copy's own commitments never overlap, so each can independently take any candidate that every
   * module reports free for its interval; no reshuffling of other copies is needed.
   */
  private void moveCommitments(UUID branchId, UUID copyId, List<UUID> candidates) {
    for (CopyCommitments owner : commitments) {
      for (Commitment commitment : owner.openCommitments(branchId, copyId)) {
        UUID replacement =
            candidates.stream()
                .filter(
                    candidate ->
                        commitments.stream()
                            .allMatch(
                                source ->
                                    source.isFree(
                                        branchId,
                                        candidate,
                                        commitment.from(),
                                        commitment.until())))
                .findFirst()
                .orElseThrow(
                    () ->
                        new ResponseStatusException(
                            HttpStatus.CONFLICT,
                            "No usable copy can cover this copy's reservations or active rental"));
        owner.move(commitment, branchId, copyId, replacement);
      }
    }
  }

  private Map<UUID, String> latestReasons(Collection<UUID> copyIds) {
    if (copyIds.isEmpty()) return Map.of();
    return conditionChanges.latestOf(copyIds).stream()
        .filter(change -> change.getReason() != null)
        .collect(
            Collectors.toMap(
                RentalCopyConditionChange::getCopyId, RentalCopyConditionChange::getReason));
  }

  private static ResponseStatusException missing() {
    return new ResponseStatusException(HttpStatus.NOT_FOUND, "Inventory item not found");
  }
}
