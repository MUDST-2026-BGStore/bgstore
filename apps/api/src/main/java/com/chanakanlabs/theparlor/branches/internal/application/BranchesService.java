package com.chanakanlabs.theparlor.branches.internal.application;

import com.chanakanlabs.theparlor.branches.internal.domain.BranchOpeningHours;
import com.chanakanlabs.theparlor.branches.internal.domain.BranchOpeningHoursId;
import com.chanakanlabs.theparlor.branches.internal.persistence.BranchOpeningHoursRepository;
import com.chanakanlabs.theparlor.branches.internal.persistence.BranchRepository;
import com.chanakanlabs.theparlor.branches.spi.TableCommitments;
import com.chanakanlabs.theparlor.branches.spi.WeeklyHours;
import com.chanakanlabs.theparlor.contracts.model.Branch;
import com.chanakanlabs.theparlor.contracts.model.BranchPage;
import com.chanakanlabs.theparlor.contracts.model.CreateBranchRequest;
import com.chanakanlabs.theparlor.contracts.model.OpeningPeriod;
import com.chanakanlabs.theparlor.contracts.model.UpdateBranchRequest;
import com.chanakanlabs.theparlor.identity.CurrentUser;
import com.chanakanlabs.theparlor.shared.KeysetCursor;
import java.time.DayOfWeek;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** The branch directory: contact details, weekly opening hours and activation. */
@Service
@Transactional(readOnly = true)
public class BranchesService {
  private static final String LIST = "branches";

  private final BranchRepository branches;
  private final BranchOpeningHoursRepository openingHours;
  private final List<TableCommitments> commitments;
  private final CurrentUser currentUser;

  public BranchesService(
      BranchRepository branches,
      BranchOpeningHoursRepository openingHours,
      List<TableCommitments> commitments,
      CurrentUser currentUser) {
    this.branches = branches;
    this.openingHours = openingHours;
    this.commitments = commitments;
    this.currentUser = currentUser;
  }

  /** Inactive branches are hidden from everyone except administrators, who must reopen them. */
  public Branch get(UUID branchId) {
    var branch = branches.findById(branchId).orElseThrow(BranchesService::missing);
    if (!branch.isActive() && !administrator()) throw missing();
    return toContract(branch, hoursOf(List.of(branchId)).getOrDefault(branchId, List.of()));
  }

  public BranchPage list(Integer requestedLimit, String cursor) {
    int limit = KeysetCursor.limit(requestedLimit);
    UUID after = KeysetCursor.afterId(cursor, LIST, "");
    Specification<com.chanakanlabs.theparlor.branches.internal.domain.Branch> active =
        (root, query, cb) ->
            cb.and(
                cb.isTrue(root.get("active")),
                after == null ? cb.conjunction() : cb.greaterThan(root.get("id"), after));
    var rows = branches.findBy(active, query -> query.sortBy(Sort.by("id")).limit(limit + 1).all());
    var page = rows.stream().limit(limit).toList();
    var hours = hoursOf(page.stream().map(branch -> branch.getId()).toList());
    var result =
        new BranchPage(
            page.stream()
                .map(branch -> toContract(branch, hours.getOrDefault(branch.getId(), List.of())))
                .toList());
    if (rows.size() > limit)
      result.setNextCursor(KeysetCursor.next(LIST, "", page.getLast().getId()));
    return result;
  }

  @Transactional
  @PreAuthorize("hasRole('ADMIN')")
  public Branch create(CreateBranchRequest request) {
    var hours = weeklyHours(request.getOpeningHours());
    var created =
        com.chanakanlabs.theparlor.branches.internal.domain.Branch.create(
            request.getName(),
            request.getAddress(),
            request.getPhone(),
            Boolean.TRUE.equals(request.getActive()));
    created.changeDelivery(Boolean.TRUE.equals(request.getFulfillsDelivery()));
    var branch = saveUniqueDelivery(created);
    openingHours.saveAllAndFlush(
        hours.periods().stream()
            .map(period -> BranchOpeningHours.of(branch.getId(), period))
            .toList());
    return toContract(branch, hours.periods());
  }

  @Transactional
  @PreAuthorize("hasRole('ADMIN')")
  public Branch update(UUID branchId, UpdateBranchRequest request) {
    if (request.getName() == null
        && request.getAddress() == null
        && request.getPhone() == null
        && request.getOpeningHours() == null
        && request.getFulfillsDelivery() == null
        && request.getActive() == null)
      throw new IllegalArgumentException("At least one update property is required");
    // The row lock serializes this change against bookings, which lock the branch they commit.
    var branch = branches.lockById(branchId).orElseThrow(BranchesService::missing);
    branch.changeDetails(
        request.getName() == null ? branch.getName() : request.getName(),
        request.getAddress() == null ? branch.getAddress() : request.getAddress(),
        request.getPhone() == null ? branch.getPhoneNumber() : request.getPhone());

    var stored = openingHours.findByIdBranchIdIn(List.of(branchId));
    var periods = stored.stream().map(BranchOpeningHours::toPeriod).toList();
    if (request.getOpeningHours() != null) {
      var hours = weeklyHours(request.getOpeningHours());
      if (!hours.equals(new WeeklyHours(periods))) {
        if (commitments.stream().anyMatch(c -> c.hasOpenCommitmentsOutside(branchId, hours)))
          throw conflict("Opening hours would exclude a confirmed reservation or active rental");
        replaceHours(branchId, stored, hours);
        periods = hours.periods();
      }
    }
    if (request.getActive() != null && request.getActive() != branch.isActive()) {
      if (request.getActive()) branch.activate();
      else {
        if (commitments.stream().anyMatch(c -> c.hasOpenCommitments(branchId)))
          throw conflict("Branch has a confirmed reservation or active rental");
        branch.deactivate();
      }
    }
    if (request.getFulfillsDelivery() != null) branch.changeDelivery(request.getFulfillsDelivery());
    saveUniqueDelivery(branch);
    return toContract(branch, periods);
  }

  private com.chanakanlabs.theparlor.branches.internal.domain.Branch saveUniqueDelivery(
      com.chanakanlabs.theparlor.branches.internal.domain.Branch branch) {
    try {
      return branches.saveAndFlush(branch);
    } catch (org.springframework.dao.DataIntegrityViolationException exception) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "Another branch already fulfills delivery", exception);
    }
  }

  private void replaceHours(UUID branchId, List<BranchOpeningHours> stored, WeeklyHours hours) {
    Map<BranchOpeningHoursId, BranchOpeningHours> existing =
        stored.stream().collect(Collectors.toMap(BranchOpeningHours::getId, Function.identity()));
    var added = new java.util.ArrayList<BranchOpeningHours>();
    for (WeeklyHours.Period period : hours.periods()) {
      var row = BranchOpeningHours.of(branchId, period);
      var kept = existing.remove(row.getId());
      if (kept == null) added.add(row);
      else kept.closeAt(period);
    }
    openingHours.deleteAll(existing.values());
    openingHours.saveAll(added);
    openingHours.flush();
  }

  private Map<UUID, List<WeeklyHours.Period>> hoursOf(List<UUID> branchIds) {
    if (branchIds.isEmpty()) return Map.of();
    var result = new HashMap<UUID, List<WeeklyHours.Period>>();
    for (BranchOpeningHours row : openingHours.findByIdBranchIdIn(branchIds))
      result
          .computeIfAbsent(row.getId().branchId(), id -> new java.util.ArrayList<>())
          .add(row.toPeriod());
    result.replaceAll((id, periods) -> new WeeklyHours(periods).periods());
    return result;
  }

  private static WeeklyHours weeklyHours(List<OpeningPeriod> periods) {
    if (periods == null) throw new IllegalArgumentException("Opening hours are required");
    if (periods.stream().anyMatch(java.util.Objects::isNull))
      throw new IllegalArgumentException("Opening periods cannot be null");
    return new WeeklyHours(
        periods.stream()
            .map(
                period ->
                    WeeklyHours.Period.parse(
                        period.getDayOfWeek() == null
                            ? null
                            : DayOfWeek.valueOf(period.getDayOfWeek().name()),
                        period.getOpensAt(),
                        period.getClosesAt()))
            .toList());
  }

  private static Branch toContract(
      com.chanakanlabs.theparlor.branches.internal.domain.Branch branch,
      List<WeeklyHours.Period> periods) {
    var result =
        new Branch(
            branch.getId(),
            branch.getName(),
            branch.getAddress(),
            periods.stream()
                .map(
                    period ->
                        new OpeningPeriod(
                            OpeningPeriod.DayOfWeekEnum.valueOf(period.day().name()),
                            period.opensAt(),
                            period.closesAt()))
                .toList(),
            branch.isActive(),
            branch.isFulfillsDelivery());
    result.setPhone(branch.getPhoneNumber());
    return result;
  }

  private boolean administrator() {
    return currentUser.find().filter(user -> user.roles().contains("admin")).isPresent();
  }

  private static ResponseStatusException missing() {
    return new ResponseStatusException(HttpStatus.NOT_FOUND, "Branch not found");
  }

  private static ResponseStatusException conflict(String reason) {
    return new ResponseStatusException(HttpStatus.CONFLICT, reason);
  }
}
