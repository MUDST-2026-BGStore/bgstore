package com.chanakanlabs.bgstore.inventory;

import com.chanakanlabs.bgstore.contract.model.GameCategory;
import com.chanakanlabs.bgstore.contract.model.GameLifecycle;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.lang.Nullable;

/** A catalogue row as stored, without the per-branch stock that hangs off it. */
record StoredGame(
    UUID id,
    String title,
    @Nullable String description,
    GameCategory category,
    int minPlayers,
    int maxPlayers,
    @Nullable Integer playTimeMinutes,
    @Nullable String difficulty,
    List<String> tags,
    GameLifecycle lifecycle,
    OffsetDateTime addedAt,
    @Nullable OffsetDateTime lastPlayedAt) {}
