package com.chanakanlabs.theparlor.orders;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ShippingPolicyTests {
  @Test
  void chargesBelowTheThresholdAndIsFreeAtTheThreshold() {
    assertThat(ShippingPolicy.feeMinor(true, 149_999)).isEqualTo(5_000);
    assertThat(ShippingPolicy.feeMinor(true, 150_000)).isZero();
    assertThat(ShippingPolicy.feeMinor(false, 149_999)).isZero();
  }
}
