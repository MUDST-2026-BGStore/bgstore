package com.chanakanlabs.theparlor.orders.internal.domain;

import com.chanakanlabs.theparlor.shared.ContactStorageLimits;
import com.chanakanlabs.theparlor.shared.EnumStorageLengths;
import com.chanakanlabs.theparlor.shared.WorkflowTextLimits;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

@Entity
@DynamicUpdate
@Table(name = "orders")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Order {
  /** Maximum stored order contact email length. */
  public static final int MAX_CONTACT_EMAIL_LENGTH = 254;

  @Id
  @GeneratedValue
  @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
  private UUID id;

  @Column(name = "branch_id", nullable = false)
  private UUID branchId;

  @Column(name = "customer_id")
  private UUID customerId;

  @Column(
      name = "contact_name",
      nullable = false,
      length = ContactStorageLimits.MAX_SNAPSHOT_NAME_LENGTH)
  private String contactName;

  @Column(name = "contact_email", nullable = false, length = MAX_CONTACT_EMAIL_LENGTH)
  private String contactEmail;

  @Column(name = "contact_phone", nullable = false, length = ContactStorageLimits.MAX_PHONE_LENGTH)
  private String contactPhone;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = EnumStorageLengths.SOURCE)
  private OrderSource source;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = EnumStorageLengths.STATUS)
  private OrderStatus status;

  @Enumerated(EnumType.STRING)
  @Column(
      name = "fulfillment_method",
      nullable = false,
      length = EnumStorageLengths.FULFILLMENT_METHOD)
  private OrderFulfillmentMethod fulfillmentMethod;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "shipping_address", columnDefinition = "jsonb")
  @Getter(AccessLevel.NONE)
  private Map<String, Object> shippingAddress;

  @Column(length = WorkflowTextLimits.MAX_NOTES_LENGTH)
  private String notes;

  @Column(name = "total_minor", nullable = false)
  private Long totalMinor;

  @Column(name = "created_by_subject", nullable = false)
  private String createdBySubject;

  @Column(name = "fulfilled_at")
  private Instant fulfilledAt;

  @Column(name = "fulfilled_by")
  private String fulfilledBy;

  @Column(name = "cancelled_at")
  private Instant cancelledAt;

  @Column(name = "cancelled_by")
  private String cancelledBy;

  @Column(name = "cancellation_reason", length = WorkflowTextLimits.MAX_CANCELLATION_REASON_LENGTH)
  private String cancellationReason;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  public Map<String, Object> getShippingAddress() {
    return shippingAddress == null ? null : copyJsonObject(shippingAddress);
  }

  private static Map<String, Object> copyJsonObject(Map<String, Object> source) {
    var copy = new java.util.LinkedHashMap<String, Object>();
    source.forEach((key, value) -> copy.put(key, copyJsonValue(value)));
    return java.util.Collections.unmodifiableMap(copy);
  }

  private static Object copyJsonValue(Object value) {
    if (value instanceof Map<?, ?> map) {
      var copy = new java.util.LinkedHashMap<Object, Object>();
      map.forEach((key, nested) -> copy.put(key, copyJsonValue(nested)));
      return java.util.Collections.unmodifiableMap(copy);
    }
    if (value instanceof java.util.List<?> list) {
      return list.stream().map(Order::copyJsonValue).toList();
    }
    return value; // JSON scalar values (including null) are immutable.
  }
}
