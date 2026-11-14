package com.chanakanlabs.theparlor.orders;

import com.chanakanlabs.theparlor.contracts.model.Order;
import com.chanakanlabs.theparlor.contracts.model.ShippingAddress;
import java.util.List;
import java.util.UUID;

/**
 * Module interface for turning a customer's selection into a placed order with its payment settled.
 * Call inside the caller's transaction: stock is held, the order is written and the payment is
 * started together, and any failure leaves none of them behind.
 */
public interface OrderPlacement {
  /**
   * Places a customer order. Throws a 400 for a payment method the buyer may not use, a 409 when
   * any line cannot be held in full, a 402 when the payment is declined and a 502 when the payment
   * provider fails.
   */
  Order place(Placement placement);

  /** A previously placed order, for replaying an idempotent placement. */
  Order get(UUID orderId);

  record Line(UUID editionId, int quantity) {}

  /**
   * CARD authorizes paymentToken now. PROMPTPAY issues a QR the customer must pay within the
   * payments module's PromptPay lifetime, or the order is cancelled. COD waits for cash at the
   * door. PROMPTPAY and COD need a signed-in buyer; COD also needs a shipping address.
   */
  enum PaymentMethod {
    CARD,
    PROMPTPAY,
    COD
  }

  /**
   * accountId and subject identify a signed-in buyer and are both null for a guest. A null
   * shippingAddress means pickup at branchId; otherwise branchId is the delivery branch.
   * paymentToken is required for CARD and must be null otherwise; a null paymentMethod is CARD.
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
      PaymentMethod paymentMethod,
      String paymentToken) {
    public Placement {
      if (paymentMethod == null) paymentMethod = PaymentMethod.CARD;
    }
  }
}
