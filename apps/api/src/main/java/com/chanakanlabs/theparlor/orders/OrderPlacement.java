package com.chanakanlabs.theparlor.orders;

import com.chanakanlabs.theparlor.contracts.model.Order;
import com.chanakanlabs.theparlor.contracts.model.ShippingAddress;
import java.util.List;
import java.util.UUID;

/**
 * Module interface for turning a customer's selection into a paid, placed order. Call inside the
 * caller's transaction: stock is held, the order is written and the card is authorized together,
 * and any failure leaves none of them behind.
 */
public interface OrderPlacement {
  /**
   * Places a customer order paid by card. Throws a 409 when any line cannot be held in full, a 402
   * when the payment is declined and a 502 when the payment provider fails.
   */
  Order place(Placement placement);

  /** A previously placed order, for replaying an idempotent placement. */
  Order get(UUID orderId);

  record Line(UUID editionId, int quantity) {}

  /**
   * accountId and subject identify a signed-in buyer and are both null for a guest. A null
   * shippingAddress means pickup at branchId; otherwise branchId is the delivery branch.
   */
  record Placement(
      UUID branchId,
      UUID accountId,
      String subject,
      String contactName,
      String contactEmail,
      String contactPhone,
      List<Line> lines,
      ShippingAddress shippingAddress,
      String notes,
      String paymentToken) {}
}
