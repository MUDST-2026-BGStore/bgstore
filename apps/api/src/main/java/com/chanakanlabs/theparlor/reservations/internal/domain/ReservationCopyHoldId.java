package com.chanakanlabs.theparlor.reservations.internal.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.UUID;

@Embeddable
public record ReservationCopyHoldId(
    @Column(name = "reservation_id") UUID reservationId, @Column(name = "copy_id") UUID copyId)
    implements Serializable {}
