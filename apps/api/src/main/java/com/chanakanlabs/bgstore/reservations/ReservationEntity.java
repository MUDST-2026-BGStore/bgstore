package com.chanakanlabs.bgstore.reservations;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import org.hibernate.annotations.CreationTimestamp;
import org.springframework.lang.Nullable;

@Entity
@Table(name = "reservation")
@SuppressWarnings("NullAway.Init")
public class ReservationEntity {

  @Id
  @Column(name = "id", nullable = false, length = 64)
  private String id;

  @Column(name = "client_subject", nullable = false)
  private String clientSubject;

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

  @CreationTimestamp
  @Column(name = "created_at", nullable = false)
  private OffsetDateTime createdAt;

  protected ReservationEntity() {}

  public ReservationEntity(String id) {
    this.id = id;
  }

  public ReservationEntity(
      String id,
      String clientSubject,
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
