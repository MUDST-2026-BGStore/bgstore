package com.chanakanlabs.bgstore.inventory;

import com.chanakanlabs.bgstore.contract.model.GameAvailability;
import com.chanakanlabs.bgstore.contract.model.GameCategory;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * One inventory list row, already rolled up over the branches the filter selected.
 *
 * @param singleBranchId the only stocked branch, or null when the roll-up spans several branches or
 *     none
 * @param coverImageUrl the game's first photo, or null when it has none
 */
record GameSummaryRow(
    UUID id,
    LocalizedText title,
    GameCategory category,
    int minPlayers,
    int maxPlayers,
    @Nullable Integer playTimeMinutes,
    @Nullable String coverImageUrl,
    int copies,
    int available,
    int branchCount,
    @Nullable UUID singleBranchId,
    GameAvailability status) {}
