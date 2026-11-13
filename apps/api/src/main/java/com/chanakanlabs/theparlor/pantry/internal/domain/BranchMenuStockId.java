package com.chanakanlabs.theparlor.pantry.internal.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.UUID;

@Embeddable
public record BranchMenuStockId(
    @Column(name = "branch_id") UUID branchId, @Column(name = "menu_item_id") UUID menuItemId)
    implements Serializable {}
