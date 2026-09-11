package com.chanakanlabs.bgstore.inventory;

import com.chanakanlabs.bgstore.contract.model.GameAvailability;
import com.chanakanlabs.bgstore.contract.model.GameCategory;
import java.util.UUID;
import org.springframework.lang.Nullable;

/**
 * One inventory list row, already rolled up over the branches the filter selected.
 *
 * @param singleBranchId the only stocked branch, or null when the roll-up spans several branches or
 *     none
 */
record GameSummaryRow(
    UUID id,
    LocalizedText title,
    GameCategory category,
    int minPlayers,
    int maxPlayers,
    int copies,
    int available,
    int branchCount,
    @Nullable UUID singleBranchId,
    GameAvailability status) {}
