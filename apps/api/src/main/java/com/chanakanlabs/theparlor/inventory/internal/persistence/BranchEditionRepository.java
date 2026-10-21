package com.chanakanlabs.theparlor.inventory.internal.persistence;

import com.chanakanlabs.theparlor.inventory.internal.domain.BranchEdition;
import com.chanakanlabs.theparlor.inventory.internal.domain.BranchEditionId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BranchEditionRepository extends JpaRepository<BranchEdition, BranchEditionId> {}
