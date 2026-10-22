package com.chanakanlabs.theparlor.orders.internal.domain;

import static org.assertj.core.api.Assertions.*;

import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class OrderEncapsulationTests {
  @Test
  void shippingAddressCannotMutateManagedJsonIncludingNestedObjectsOrNulls() {
    var order = new Order();
    var nested = new HashMap<String, Object>();
    nested.put("city", "Bangkok");
    nested.put("optional", null);
    var address = new HashMap<String, Object>();
    address.put("details", nested);
    address.put("lines", new ArrayList<>(List.of("First")));
    ReflectionTestUtils.setField(order, "shippingAddress", address);
    var view = order.getShippingAddress();
    assertThatThrownBy(view::clear).isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(() -> ((Map<?, ?>) view.get("details")).clear())
        .isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(() -> ((List<?>) view.get("lines")).clear())
        .isInstanceOf(UnsupportedOperationException.class);
    assertThat(((Map<?, ?>) view.get("details")).containsKey("optional")).isTrue();
    nested.put("city", "Changed");
    assertThat(((Map<?, ?>) view.get("details")).get("city")).isEqualTo("Bangkok");
  }
}
