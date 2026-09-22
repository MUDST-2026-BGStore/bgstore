package com.chanakanlabs.bgstore.reservations;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.jspecify.annotations.Nullable;

@Entity
@Table(name = "reservation")
@SuppressWarnings("NullAway.Init")
public class ReservationEntity {

  @Id
  @Column(name = "id", nullable = false, length = 64)
  private String id;

  @Column(name = "client_subject")
  private @Nullable String clientSubject;

  @Column(name = "branch_id", nullable = false)
  private UUID branchId;

  @Column(name = "title", nullable = false)
  private String title;

  @Column(name = "reservation_date", nullable = false, length = 64)
  private String reservationDate;

  @Column(name = "time_slot", nullable = false, length = 64)
  private String timeSlot;

  @Column(name = "party_size", nullable = false)
  private int partySize;

  @Column(name = "table_id", nullable = false)
  private long tableId;

  @Column(name = "table_name", nullable = false, length = 100)
  private String tableName;

  @Column(name = "seats", nullable = false)
  private int seats;

  @Column(name = "rate_per_hour", nullable = false)
  private int ratePerHour;

  @Column(name = "status", nullable = false, length = 16)
  private String status;

  @Column(name = "customer_name", nullable = false)
  private String customerName;

  @Column(name = "phone_number", nullable = false, length = 64)
  private String phoneNumber;

  @Column(name = "check_in_time", nullable = false, length = 64)
  private String checkInTime;

  @Column(name = "actual_check_out", nullable = false, length = 64)
  private String actualCheckOut;

  @Column(name = "overtime_minutes", nullable = false)
  private int overtimeMinutes;

  @Column(name = "total_price", nullable = false)
  private int totalPrice;

  @Column(name = "can_cancel", nullable = false)
  private boolean canCancel;

  @Column(name = "thumbnail_url", length = 512)
  private @Nullable String thumbnailUrl;

  /** How the confirmed fee was settled; null until staff check the session out. */
  @Column(name = "payment_method", length = 32)
  private @Nullable String paymentMethod;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false)
  private OffsetDateTime createdAt;

  protected ReservationEntity() {}

  public ReservationEntity(String id) {
    this.id = id;
  }

  public ReservationEntity(
      String id,
      UUID branchId,
      @Nullable String clientSubject,
      String title,
      String reservationDate,
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
      OffsetDateTime createdAt) {
    this(
        id,
        clientSubject,
        title,
        reservationDate,
        timeSlot,
        partySize,
        tableId,
        tableName,
        seats,
        ratePerHour,
        status,
        customerName,
        phoneNumber,
        checkInTime,
        actualCheckOut,
        overtimeMinutes,
        totalPrice,
        canCancel,
        thumbnailUrl,
        createdAt);
    this.branchId = branchId;
  }

  public ReservationEntity(
      String id,
      @Nullable String clientSubject,
      String title,
      String reservationDate,
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
      OffsetDateTime createdAt) {
    this.id = id;
    this.clientSubject = clientSubject;
    this.title = title;
    this.reservationDate = reservationDate;
    this.timeSlot = timeSlot;
    this.partySize = partySize;
    this.tableId = tableId;
    this.tableName = tableName;
    this.seats = seats;
    this.ratePerHour = ratePerHour;
    this.status = status;
    this.customerName = customerName;
    this.phoneNumber = phoneNumber;
    this.checkInTime = checkInTime;
    this.actualCheckOut = actualCheckOut;
    this.overtimeMinutes = overtimeMinutes;
    this.totalPrice = totalPrice;
    this.canCancel = canCancel;
    this.thumbnailUrl = thumbnailUrl;
    this.createdAt = createdAt;
  }

  public void cancel() {
    this.status = "Cancelled";
    this.canCancel = false;
  }

  /** Starts play. The instant is stored in ISO-8601 form so elapsed time stays absolute. */
  public void checkIn(Instant startedAt) {
    this.status = "CheckedIn";
    this.checkInTime = startedAt.toString();
  }

  /**
   * Closes play with the fee an authorized role confirmed. A waived fee is recorded as a zero
   * amount with the {@code Waived} method.
   */
  public void checkOut(
      Instant endedAt, int finalAmount, int overtimeMinutes, String paymentMethod) {
    this.status = "Completed";
    this.actualCheckOut = endedAt.toString();
    this.totalPrice = finalAmount;
    this.overtimeMinutes = overtimeMinutes;
    this.paymentMethod = paymentMethod;
    this.canCancel = false;
  }

  public UUID branchId() {
    return branchId;
  }

  public String status() {
    return status;
  }

  public String checkInTime() {
    return checkInTime;
  }

  public String reservationDate() {
    return reservationDate;
  }

  public String timeSlot() {
    return timeSlot;
  }

  public ReservationRecordData toRecord() {
    return new ReservationRecordData(
        id,
        clientSubject,
        title,
        reservationDate,
        timeSlot,
        partySize,
        tableId,
        tableName,
        seats,
        ratePerHour,
        status,
        customerName,
        phoneNumber,
        checkInTime,
        actualCheckOut,
        overtimeMinutes,
        totalPrice,
        canCancel,
        thumbnailUrl,
        createdAt);
  }
}
