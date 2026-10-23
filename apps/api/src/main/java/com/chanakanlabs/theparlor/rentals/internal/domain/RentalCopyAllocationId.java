package com.chanakanlabs.theparlor.rentals.internal.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.UUID;

@Embeddable
public record RentalCopyAllocationId(
    @Column(name = "rental_id") UUID rentalId, @Column(name = "copy_id") UUID copyId)
    implements Serializable {}
