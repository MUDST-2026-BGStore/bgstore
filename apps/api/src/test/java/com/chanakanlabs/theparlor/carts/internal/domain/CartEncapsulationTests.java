package com.chanakanlabs.theparlor.carts.internal.domain;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class CartEncapsulationTests {
  @Test
  void tokenHashIsDefensivelyCopied() {
    var cart = new Cart();
    ReflectionTestUtils.setField(cart, "tokenHash", new byte[] {1, 2});
    cart.getTokenHash()[0] = 9;
    assertThat(cart.getTokenHash()).containsExactly((byte) 1, (byte) 2);
  }
}
