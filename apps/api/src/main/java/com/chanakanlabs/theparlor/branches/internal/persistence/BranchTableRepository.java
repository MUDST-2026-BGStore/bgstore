package com.chanakanlabs.theparlor.branches.internal.persistence;

import com.chanakanlabs.theparlor.branches.internal.domain.BranchTable;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BranchTableRepository extends JpaRepository<BranchTable, UUID> {}
