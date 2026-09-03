package com.chanakanlabs.bgstore.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ReturnToRequestFilterTest {

  @Test
  void acceptsOnlySafeSameOriginPaths() {
    assertThat(ReturnToRequestFilter.isSafeRelativePath("/reservations?date=2026-09-02")).isTrue();
    assertThat(ReturnToRequestFilter.isSafeRelativePath("https://example.test")).isFalse();
    assertThat(ReturnToRequestFilter.isSafeRelativePath("//example.test")).isFalse();
    assertThat(ReturnToRequestFilter.isSafeRelativePath("/\\example.test")).isFalse();
  }
}
