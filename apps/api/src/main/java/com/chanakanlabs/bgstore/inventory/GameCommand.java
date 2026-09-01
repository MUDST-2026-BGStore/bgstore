package com.chanakanlabs.bgstore.inventory;

import com.chanakanlabs.bgstore.contract.model.GameCategory;
import com.chanakanlabs.bgstore.contract.model.GameLifecycle;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.lang.Nullable;

/**
 * A create or update payload that has already passed {@link GameValidator}: values are trimmed,
 * blanks are collapsed to null, and every branch in {@code copies} is known to exist.
 */
record GameCommand(
    String title,
    @Nullable String description,
    GameCategory category,
    int minPlayers,
    int maxPlayers,
    @Nullable Integer playTimeMinutes,
    @Nullable String difficulty,
    List<String> tags,
    GameLifecycle lifecycle,
    Map<UUID, Integer> copiesByBranch) {}
