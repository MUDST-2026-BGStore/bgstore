package com.chanakanlabs.bgstore.reservations.domain;

import java.util.UUID;

/** Read model used by the future table-selection step. */
public record TableAvailability(UUID tableId, String displayName, int seats, boolean available) {}
