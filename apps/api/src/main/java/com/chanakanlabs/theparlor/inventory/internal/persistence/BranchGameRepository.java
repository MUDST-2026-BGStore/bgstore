package com.chanakanlabs.theparlor.inventory.internal.persistence;

import com.chanakanlabs.theparlor.inventory.internal.domain.BranchGame;
import com.chanakanlabs.theparlor.inventory.internal.domain.BranchGameId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BranchGameRepository extends JpaRepository<BranchGame, BranchGameId> {}
