package com.chanakanlabs.bgstore.reservations;

import java.time.OffsetDateTime;
import org.springframework.lang.Nullable;

public record ReservationRecordData(
    String id,
    String clientSubject,
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
