package com.chanakanlabs.theparlor.orders;

/** Business-wide retail shipping fee policy. Amounts are THB minor units. */
public final class ShippingPolicy {
  public static final long FLAT_FEE_MINOR = 5_000;
  public static final long FREE_FROM_MINOR = 150_000;

  private ShippingPolicy() {}

  /** Returns the shipping fee for an order with the supplied items total. */
  public static long feeMinor(boolean shipping, long itemsTotalMinor) {
    if (itemsTotalMinor < 0) throw new IllegalArgumentException("Items total must be nonnegative");
    return shipping && itemsTotalMinor < FREE_FROM_MINOR ? FLAT_FEE_MINOR : 0;
  }
}
