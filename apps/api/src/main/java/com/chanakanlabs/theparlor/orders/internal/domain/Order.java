package com.chanakanlabs.theparlor.orders.internal.domain;

import com.chanakanlabs.theparlor.orders.ShippingPolicy;
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

  @Column(name = "customer_subject")
  private String customerSubject;

  @Column(name = "contact_email", length = MAX_CONTACT_EMAIL_LENGTH)
  private String contactEmail;

  @Column(name = "contact_phone", length = ContactStorageLimits.MAX_PHONE_LENGTH)
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

  @Column(name = "shipping_fee_minor", nullable = false)
  private Long shippingFeeMinor;

  @Enumerated(EnumType.STRING)
  @Column(length = EnumStorageLengths.STATUS)
  private OrderCarrier carrier;

  @Column(name = "tracking_number", length = 60)
  private String trackingNumber;

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

  /** Who the order is for. subject is the registered customer's identity; null for guests. */
  public record Buyer(UUID customerId, String subject, String name, String email, String phone) {
    public Buyer {
      name = text(name, ContactStorageLimits.MAX_SNAPSHOT_NAME_LENGTH, "Contact name");
      if (name == null) throw new IllegalArgumentException("Contact name is required");
      email = text(email, MAX_CONTACT_EMAIL_LENGTH, "Contact email");
      phone = text(phone, ContactStorageLimits.MAX_PHONE_LENGTH, "Contact phone");
      if ((customerId == null) != (subject == null))
        throw new IllegalArgumentException("A registered buyer needs a customer and a subject");
    }
  }

  /**
   * Places an order whose stock is already held. A shipping order needs an address; a pickup order
   * must not carry one.
   */
  public static Order place(
      UUID branchId,
      Buyer buyer,
      OrderSource source,
      Map<String, Object> shippingAddress,
      String notes,
      long itemsTotalMinor,
      String createdBySubject) {
    if (itemsTotalMinor < 0) throw new IllegalArgumentException("Order total must be nonnegative");
    if (createdBySubject == null || createdBySubject.isBlank())
      throw new IllegalArgumentException("Orders need a creator");
    var order = new Order();
    order.branchId = java.util.Objects.requireNonNull(branchId);
    order.customerId = buyer.customerId();
    order.customerSubject = buyer.subject();
    order.contactName = buyer.name();
    order.contactEmail = buyer.email();
    order.contactPhone = buyer.phone();
    order.source = java.util.Objects.requireNonNull(source);
    order.status = OrderStatus.PLACED;
    order.fulfillmentMethod =
        shippingAddress == null ? OrderFulfillmentMethod.PICKUP : OrderFulfillmentMethod.SHIPPING;
    order.shippingAddress = shippingAddress == null ? null : copyJsonObject(shippingAddress);
    order.notes = text(notes, WorkflowTextLimits.MAX_NOTES_LENGTH, "Notes");
    order.shippingFeeMinor = ShippingPolicy.feeMinor(shippingAddress != null, itemsTotalMinor);
    order.totalMinor = Math.addExact(itemsTotalMinor, order.shippingFeeMinor);
    order.createdBySubject = createdBySubject;
    return order;
  }

  /** Records the carrier and tracking number before a shipping order is fulfilled. */
  public void ship(OrderCarrier carrier, String trackingNumber) {
    if (fulfillmentMethod != OrderFulfillmentMethod.SHIPPING)
      throw new IllegalStateException("A pickup order cannot be shipped");
    if (status == OrderStatus.FULFILLED) return;
    if (status == OrderStatus.CANCELLED)
      throw new IllegalStateException("A cancelled order cannot be shipped");
    String stripped = text(trackingNumber, 60, "Tracking number");
    if (stripped == null) throw new IllegalArgumentException("Tracking number is required");
    this.carrier = java.util.Objects.requireNonNull(carrier, "Carrier is required");
    this.trackingNumber = stripped;
  }

  /** Returns false when the order was already cancelled; a fulfilled order cannot be. */
  public boolean cancel(String actor, String reason, Instant now) {
    if (status == OrderStatus.CANCELLED) return false;
    if (status == OrderStatus.FULFILLED)
      throw new IllegalStateException("A fulfilled order cannot be cancelled");
    String stripped =
        text(reason, WorkflowTextLimits.MAX_CANCELLATION_REASON_LENGTH, "Cancellation reason");
    if (stripped == null) throw new IllegalArgumentException("Cancellation reason is required");
    status = OrderStatus.CANCELLED;
    cancelledAt = java.util.Objects.requireNonNull(now);
    cancelledBy = java.util.Objects.requireNonNull(actor);
    cancellationReason = stripped;
    return true;
  }

  /** Returns false when the order was already fulfilled; a cancelled order cannot be. */
  public boolean fulfill(String actor, Instant now) {
    if (status == OrderStatus.FULFILLED) return false;
    if (status == OrderStatus.CANCELLED)
      throw new IllegalStateException("A cancelled order cannot be fulfilled");
    status = OrderStatus.FULFILLED;
    fulfilledAt = java.util.Objects.requireNonNull(now);
    fulfilledBy = java.util.Objects.requireNonNull(actor);
    return true;
  }

  private static String text(String value, int maxLength, String field) {
    if (value == null || value.isBlank()) return null;
    String stripped = value.strip();
    if (stripped.length() > maxLength)
      throw new IllegalArgumentException(field + " is longer than " + maxLength + " characters");
    return stripped;
  }

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
