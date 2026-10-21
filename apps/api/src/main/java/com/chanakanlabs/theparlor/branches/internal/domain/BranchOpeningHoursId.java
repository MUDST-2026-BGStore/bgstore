package com.chanakanlabs.theparlor.branches.internal.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.UUID;

@Embeddable
public record BranchOpeningHoursId(
    @Column(name = "branch_id") UUID branchId,
    @Column(name = "day_of_week", nullable = false) @Convert(converter = DayOfWeekConverter.class)
        DayOfWeek dayOfWeek,
    @Column(name = "opens_at") LocalTime opensAt)
    implements Serializable {}
