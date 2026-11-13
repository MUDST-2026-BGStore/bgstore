package com.chanakanlabs.theparlor.inventory.internal.application;

import com.chanakanlabs.theparlor.inventory.spi.CopyCommitments;
import com.chanakanlabs.theparlor.inventory.spi.CopyCommitments.Use;
import java.time.Clock;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Combines every committing module's view of which copies are in use or held right now. */
@Component
class CopyUses {
  private final List<CopyCommitments> sources;
  private final Clock clock;

  CopyUses(List<CopyCommitments> sources, Clock clock) {
    this.sources = sources;
    this.clock = clock;
  }

  /** Committed copies of the branch at server time; a copy in use outranks a hold. */
  Map<UUID, Use> now(UUID branchId) {
    var now = clock.instant();
    var uses = new HashMap<UUID, Use>();
    for (CopyCommitments source : sources)
      source
          .usesAt(branchId, now)
          .forEach((copy, use) -> uses.merge(copy, use, CopyUses::stronger));
    return uses;
  }

  private static Use stronger(Use left, Use right) {
    return left == Use.IN_USE || right == Use.IN_USE ? Use.IN_USE : Use.RESERVED;
  }
}
