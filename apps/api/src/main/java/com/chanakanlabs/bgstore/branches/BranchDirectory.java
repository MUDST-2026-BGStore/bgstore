package com.chanakanlabs.bgstore.branches;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Read access to the branch directory for modules that reference branches. */
public interface BranchDirectory {

  /** Every branch, ordered by name. */
  List<Branch> findAll();

  Optional<Branch> findById(UUID id);
}
