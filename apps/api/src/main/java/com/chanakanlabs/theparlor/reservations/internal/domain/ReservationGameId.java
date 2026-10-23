package com.chanakanlabs.theparlor.reservations.internal.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.UUID;

@Embeddable
public record ReservationGameId(
    @Column(name = "reservation_id") UUID reservationId, @Column(name = "game_id") UUID gameId)
    implements Serializable {}
