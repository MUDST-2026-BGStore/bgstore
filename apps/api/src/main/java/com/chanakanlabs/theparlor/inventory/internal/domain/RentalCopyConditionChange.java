package com.chanakanlabs.theparlor.inventory.internal.domain;

import com.chanakanlabs.theparlor.shared.EnumStorageLengths;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UuidGenerator;

/** One append-only ledger entry: who changed a rental copy's condition, when and why. */
@Entity
@Table(name = "rental_copy_condition_changes")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RentalCopyConditionChange {
  /** Maximum condition-change reason length, matching UpdateRentalCopyRequest. */
  public static final int MAX_REASON_LENGTH = 500;

  @Id
  @GeneratedValue
  @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
  private UUID id;

  @Column(name = "branch_id", nullable = false, updatable = false)
  private UUID branchId;

  @Column(name = "copy_id", nullable = false, updatable = false)
  private UUID copyId;

  @Enumerated(EnumType.STRING)
  @Column(
      name = "from_status",
      nullable = false,
      updatable = false,
      length = EnumStorageLengths.STATUS)
  private RentalCopyOperationalStatus fromStatus;

  @Enumerated(EnumType.STRING)
  @Column(
      name = "to_status",
      nullable = false,
      updatable = false,
      length = EnumStorageLengths.STATUS)
  private RentalCopyOperationalStatus toStatus;

  @Column(updatable = false, length = MAX_REASON_LENGTH)
  private String reason;

  @Column(name = "operator_subject", nullable = false, updatable = false)
  private String operatorSubject;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  /** A reason is required whenever the copy leaves the rentable pool. */
  public static RentalCopyConditionChange record(
      RentalCopy copy, RentalCopyOperationalStatus to, String reason, String operatorSubject) {
    java.util.Objects.requireNonNull(to);
    String stripped = reason == null ? null : reason.strip();
    if (stripped != null && (stripped.isEmpty() || stripped.length() > MAX_REASON_LENGTH))
      throw new IllegalArgumentException("Invalid condition-change reason");
    if (to != RentalCopyOperationalStatus.USABLE && stripped == null)
      throw new IllegalArgumentException("A reason is required for " + to);
    if (operatorSubject == null || operatorSubject.isBlank())
      throw new IllegalArgumentException("Condition changes need an operator");
    var change = new RentalCopyConditionChange();
    change.branchId = copy.getBranchId();
    change.copyId = java.util.Objects.requireNonNull(copy.getId());
    change.fromStatus = copy.getOperationalStatus();
    change.toStatus = to;
    change.reason = stripped;
    change.operatorSubject = operatorSubject;
    return change;
  }
}
