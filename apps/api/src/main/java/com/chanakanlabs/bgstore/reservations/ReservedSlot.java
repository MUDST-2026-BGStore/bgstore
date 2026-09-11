package com.chanakanlabs.bgstore.reservations;

import java.time.OffsetDateTime;

/** The interval a reservation holds a table for. */
public record ReservedSlot(OffsetDateTime startsAt, OffsetDateTime endsAt) {}
