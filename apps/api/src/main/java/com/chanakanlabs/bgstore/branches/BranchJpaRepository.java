package com.chanakanlabs.bgstore.branches;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** CRUD over {@link BranchRecord}; Spring Data supplies the implementation. */
interface BranchJpaRepository extends JpaRepository<BranchRecord, UUID> {

  /** Derived query: Spring Data builds the ordering from the method name. */
  List<BranchRecord> findAllByOrderByNameAsc();
}
