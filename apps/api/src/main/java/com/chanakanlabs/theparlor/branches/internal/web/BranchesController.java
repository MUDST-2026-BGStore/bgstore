package com.chanakanlabs.theparlor.branches.internal.web;

import com.chanakanlabs.theparlor.contracts.api.BranchesApi;
import com.chanakanlabs.theparlor.contracts.model.*;
import java.time.*;
import java.util.*;
import org.jspecify.annotations.Nullable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class BranchesController implements BranchesApi {

  @Override
  public Branch createBranch(CreateBranchRequest createBranchRequest) {
    // TODO(branches-api): implement createBranch — POST /api/v1/branches — Create a branch
    throw new UnsupportedOperationException(
        "POST /api/v1/branches (createBranch) is not implemented yet");
  }

  @Override
  public Branch getBranch(UUID branchId) {
    // TODO(branches-api): implement getBranch — GET /api/v1/branches/{branchId} — Read an active
    // branch
    throw new UnsupportedOperationException(
        "GET /api/v1/branches/{branchId} (getBranch) is not implemented yet");
  }

  @Override
  public BranchPage listBranches(Integer limit, @Nullable String cursor) {
    // TODO(branches-api): implement listBranches — GET /api/v1/branches — List active store
    // branches
    throw new UnsupportedOperationException(
        "GET /api/v1/branches (listBranches) is not implemented yet");
  }

  @Override
  public Branch updateBranch(UUID branchId, UpdateBranchRequest updateBranchRequest) {
    // TODO(branches-api): implement updateBranch — PATCH /api/v1/branches/{branchId} — Update
    // branch details, hours or activation
    throw new UnsupportedOperationException(
        "PATCH /api/v1/branches/{branchId} (updateBranch) is not implemented yet");
  }
}
