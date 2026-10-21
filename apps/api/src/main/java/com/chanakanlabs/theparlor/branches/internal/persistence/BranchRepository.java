package com.chanakanlabs.theparlor.branches.internal.persistence;

import com.chanakanlabs.theparlor.branches.internal.domain.Branch;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BranchRepository extends JpaRepository<Branch, UUID> {}
