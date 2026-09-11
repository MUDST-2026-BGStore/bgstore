package com.chanakanlabs.bgstore.branches;

import com.chanakanlabs.bgstore.contract.api.BranchesApi;
import com.chanakanlabs.bgstore.contract.model.BranchList;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
class BranchController implements BranchesApi {

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
    return new com.chanakanlabs.bgstore.contract.model.Branch(branch.id(), branch.name());
  }
}
