package com.chanakanlabs.theparlor.branches.internal.web;

import com.chanakanlabs.theparlor.branches.internal.application.BranchesService;
import com.chanakanlabs.theparlor.contracts.api.BranchesApi;
import com.chanakanlabs.theparlor.contracts.model.*;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class BranchesController implements BranchesApi {
  private final BranchesService branches;

  public BranchesController(BranchesService branches) {
    this.branches = branches;
  }

  @Override
  public Branch createBranch(CreateBranchRequest createBranchRequest) {
    return branches.create(createBranchRequest);
  }

  @Override
  public Branch getBranch(UUID branchId) {
    return branches.get(branchId);
  }

  @Override
  public BranchPage listBranches(Integer limit, @Nullable String cursor) {
    return branches.list(limit, cursor);
  }

  @Override
  public Branch updateBranch(UUID branchId, UpdateBranchRequest updateBranchRequest) {
    return branches.update(branchId, updateBranchRequest);
  }
}
