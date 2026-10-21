package com.chanakanlabs.theparlor.inventory.internal.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.UUID;

@Embeddable
public record BranchEditionId(
    @Column(name = "branch_id") UUID branchId, @Column(name = "edition_id") UUID editionId)
    implements Serializable {}
