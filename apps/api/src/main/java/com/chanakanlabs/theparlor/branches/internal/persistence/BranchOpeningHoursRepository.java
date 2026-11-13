package com.chanakanlabs.theparlor.branches.internal.persistence;

import com.chanakanlabs.theparlor.branches.internal.domain.BranchOpeningHours;
import com.chanakanlabs.theparlor.branches.internal.domain.BranchOpeningHoursId;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BranchOpeningHoursRepository
    extends JpaRepository<BranchOpeningHours, BranchOpeningHoursId> {
  List<BranchOpeningHours> findByIdBranchIdIn(Collection<UUID> branchIds);
}
