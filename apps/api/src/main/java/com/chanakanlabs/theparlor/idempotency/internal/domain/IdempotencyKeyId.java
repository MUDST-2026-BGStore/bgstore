package com.chanakanlabs.theparlor.idempotency.internal.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.UUID;

@Embeddable
public record IdempotencyKeyId(
    @Column(name = "subject") String subject,
    @Column(name = "http_method") String httpMethod,
    @Column(name = "path") String path,
    @Column(name = "idempotency_key") UUID idempotencyKey)
    implements Serializable {}
