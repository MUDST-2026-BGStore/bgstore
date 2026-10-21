package com.chanakanlabs.theparlor.inventory.internal.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.UUID;

@Embeddable
public record BranchGameId(
    @Column(name = "branch_id") UUID branchId, @Column(name = "game_id") UUID gameId)
    implements Serializable {}
