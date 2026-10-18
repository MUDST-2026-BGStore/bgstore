package com.chanakanlabs.theparlor.shared;

/** Storage conventions for persisted workflow enums. */
public final class EnumStorageLengths {
  private EnumStorageLengths() {}

  /**
   * Column width for workflow and operational status names; not a limit on the number of states.
   */
  public static final int STATUS = 20;

  /** Column width for order and reservation source names; independent of status storage. */
  public static final int SOURCE = 20;

  /** Column width for cart and order fulfillment method names. */
  public static final int FULFILLMENT_METHOD = 10;
}
