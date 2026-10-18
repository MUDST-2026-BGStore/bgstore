package com.chanakanlabs.theparlor.shared;

/** Storage widths shared by account and customer contact snapshots. */
public final class ContactStorageLimits {
  private ContactStorageLimits() {}

  /**
   * Phone column width for accounts, customers, orders and reservations; branch phones are
   * separate.
   */
  public static final int MAX_PHONE_LENGTH = 32;

  /** Contact name width in order and reservation snapshots; not the guest request limit. */
  public static final int MAX_SNAPSHOT_NAME_LENGTH = 150;
}
