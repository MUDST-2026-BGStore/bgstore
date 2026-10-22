package com.chanakanlabs.theparlor.carts.internal.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.UUID;

@Embeddable
public record CartItemId(
    @Column(name = "cart_id") UUID cartId, @Column(name = "edition_id") UUID editionId)
    implements Serializable {}
