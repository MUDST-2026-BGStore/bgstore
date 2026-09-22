package com.chanakanlabs.bgstore.reservations;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;

/**
 * A durable client request for staff attention during a checked-in session.
 *
 * <p>Requests are recorded rather than actioned: they never close a session or stop billing. The
 * client-supplied {@code requestId} is unique so a retried submission resolves to the original
 * receipt.
 */
@Entity
@Table(name = "session_assistance_request")
@SuppressWarnings("NullAway.Init")
public class SessionAssistanceRequestEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "request_id", nullable = false)
  private UUID requestId;

  @Column(name = "reservation_id", nullable = false, length = 64)
  private String reservationId;

  @Column(name = "kind", nullable = false, length = 32)
  private String kind;

  @Column(name = "requested_by_subject", nullable = false)
  private String requestedBySubject;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false)
  private OffsetDateTime createdAt;

  protected SessionAssistanceRequestEntity() {}

  public SessionAssistanceRequestEntity(
      UUID requestId, String reservationId, String kind, String requestedBySubject) {
    this.requestId = requestId;
    this.reservationId = reservationId;
    this.kind = kind;
    this.requestedBySubject = requestedBySubject;
  }

  public UUID requestId() {
    return requestId;
  }

  public String reservationId() {
    return reservationId;
  }

  public String kind() {
    return kind;
  }
}
