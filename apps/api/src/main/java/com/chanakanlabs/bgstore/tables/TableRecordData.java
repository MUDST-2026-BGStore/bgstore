package com.chanakanlabs.bgstore.tables;

import java.time.OffsetDateTime;
import org.springframework.lang.Nullable;

public record TableRecordData(
    @Nullable Long id,
    String name,
    String branch,
    int capacity,
    String shape,
    String status,
    boolean active,
    String zone,
    OffsetDateTime lastUpdated) {}
