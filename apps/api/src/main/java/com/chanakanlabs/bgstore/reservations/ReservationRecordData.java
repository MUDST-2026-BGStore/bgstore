package com.chanakanlabs.bgstore.reservations;

import java.time.OffsetDateTime;
import org.jspecify.annotations.Nullable;

public record ReservationRecordData(
    String id,
    @Nullable String clientSubject,
    String title,
    String date,
    String timeSlot,
    int partySize,
    long tableId,
    String tableName,
    int seats,
    int ratePerHour,
    String status,
    String customerName,
    String phoneNumber,
    String checkInTime,
    String actualCheckOut,
    int overtimeMinutes,
    int totalPrice,
    boolean canCancel,
    @Nullable String thumbnailUrl,
    OffsetDateTime createdAt) {}
