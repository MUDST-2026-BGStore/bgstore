package com.chanakanlabs.bgstore.branches;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional(readOnly = true)
class JpaBranchDirectory implements BranchDirectory {

  private final BranchJpaRepository branches;

  JpaBranchDirectory(BranchJpaRepository branches) {
    this.branches = branches;
  }

  @Override
  public List<Branch> findAll() {
    return branches.findAllByOrderByNameAsc().stream().map(BranchRecord::toBranch).toList();
  }

  @Override
  public Optional<Branch> findById(UUID id) {
    return branches.findById(id).map(BranchRecord::toBranch);
  }
}
