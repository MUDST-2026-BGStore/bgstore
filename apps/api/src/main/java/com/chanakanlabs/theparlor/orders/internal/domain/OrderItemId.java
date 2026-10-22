package com.chanakanlabs.theparlor.orders.internal.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.UUID;

@Embeddable
public record OrderItemId(
    @Column(name = "order_id") UUID orderId, @Column(name = "edition_id") UUID editionId)
    implements Serializable {}
