package com.chanakanlabs.bgstore.branches;

import com.chanakanlabs.bgstore.contract.api.BranchesApi;
import com.chanakanlabs.bgstore.contract.model.BranchList;
import com.chanakanlabs.bgstore.contract.model.CreateBranchRequest;
import com.chanakanlabs.bgstore.identity.AccessPolicy;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import org.jspecify.annotations.Nullable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
class BranchController implements BranchesApi {

  /** The contract publishes hours to the minute. */
  private static final DateTimeFormatter HOURS = DateTimeFormatter.ofPattern("HH:mm");

  private final BranchDirectory branches;
  private final BranchManagementService management;
  private final AccessPolicy accessPolicy;

  BranchController(
      BranchDirectory branches, BranchManagementService management, AccessPolicy accessPolicy) {
    this.branches = branches;
    this.management = management;
    this.accessPolicy = accessPolicy;
  }

  @Override
  public ResponseEntity<BranchList> listBranches() {
    var items =
        branches.findAll().stream()
            .filter(branch -> accessPolicy.canAccessBranch(branch.id()))
            .map(BranchController::toResponse)
            .toList();

    return ResponseEntity.ok(new BranchList(items));
  }

  @Override
  public ResponseEntity<com.chanakanlabs.bgstore.contract.model.Branch> createBranch(
      CreateBranchRequest request) {
    var created =
        management.createBranch(
            request.getName(),
            request.getAddress(),
            parseHours(request.getOpensAt()),
            parseHours(request.getClosesAt()));
    return ResponseEntity.status(org.springframework.http.HttpStatus.CREATED)
        .body(toResponse(created));
  }

  /** The contract model shares its simple name with the domain record, hence the qualified type. */
  private static com.chanakanlabs.bgstore.contract.model.Branch toResponse(Branch branch) {
    var response =
        new com.chanakanlabs.bgstore.contract.model.Branch(
            branch.id(),
            branch.name(),
            com.chanakanlabs.bgstore.contract.model.BranchStatus.valueOf(branch.status().name()));
    response.setAddress(branch.address());
    response.setOpensAt(hoursOf(branch.opensAt()));
    response.setClosesAt(hoursOf(branch.closesAt()));
    response.setPhone(branch.phone());
    response.setLatitude(branch.latitude());
    response.setLongitude(branch.longitude());
    return response;
  }

  private static @Nullable String hoursOf(@Nullable LocalTime time) {
    return time == null ? null : time.format(HOURS);
  }

  private static @Nullable LocalTime parseHours(@Nullable String value) {
    return value == null || value.isBlank() ? null : LocalTime.parse(value, HOURS);
  }
}
