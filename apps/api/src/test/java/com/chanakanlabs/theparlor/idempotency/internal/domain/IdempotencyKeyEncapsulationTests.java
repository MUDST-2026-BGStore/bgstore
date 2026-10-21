package com.chanakanlabs.theparlor.idempotency.internal.domain;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class IdempotencyKeyEncapsulationTests {
  @Test
  void requestHashIsDefensivelyCopied() {
    var key = new IdempotencyKey();
    ReflectionTestUtils.setField(key, "requestHash", new byte[] {1, 2});
    key.getRequestHash()[0] = 9;
    assertThat(key.getRequestHash()).containsExactly((byte) 1, (byte) 2);
  }
}
