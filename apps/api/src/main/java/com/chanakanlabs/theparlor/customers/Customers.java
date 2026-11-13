package com.chanakanlabs.theparlor.customers;

import java.util.UUID;

/** Module interface for the business record that orders and reservations attach to. */
public interface Customers {
  /** The account's customer record, created on first use. Call inside a transaction. */
  UUID forAccount(UUID accountId);
}
