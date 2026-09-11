package com.chanakanlabs.bgstore.branches;

import com.chanakanlabs.bgstore.contract.api.BranchesApi;
import com.chanakanlabs.bgstore.contract.model.BranchList;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
class BranchController implements BranchesApi {

  /** The contract publishes hours to the minute. */
  private static final DateTimeFormatter HOURS = DateTimeFormatter.ofPattern("HH:mm");

  private final BranchDirectory branches;

  BranchController(BranchDirectory branches) {
    this.branches = branches;
  }

  @Override
  public ResponseEntity<BranchList> listBranches() {
    var items = branches.findAll().stream().map(BranchController::toResponse).toList();

    return ResponseEntity.ok(new BranchList(items));
  }

  /** The contract model shares its simple name with the domain record, hence the qualified type. */
  private static com.chanakanlabs.bgstore.contract.model.Branch toResponse(Branch branch) {
    var response = new com.chanakanlabs.bgstore.contract.model.Branch(branch.id(), branch.name());
    response.setAddress(branch.address());
    response.setOpensAt(hoursOf(branch.opensAt()));
    response.setClosesAt(hoursOf(branch.closesAt()));
    return response;
  }

  private static @Nullable String hoursOf(@Nullable LocalTime time) {
    return time == null ? null : time.format(HOURS);
  }
}
