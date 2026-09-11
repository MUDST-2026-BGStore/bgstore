package com.chanakanlabs.bgstore.playsessions.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * Active-session read model. Timestamps are absolute instants, displayed in Asia/Bangkok by
 * clients. Elapsed time is derived from startedAt rather than persisted as a ticking counter.
 */
public record ActiveSessionSnapshot(
    UUID id,
    UUID locationId,
    String locationName,
    UUID tableId,
    String tableName,
    Instant startedAt,
    int partySize,
    SessionFeeEstimate feeEstimate) {}
