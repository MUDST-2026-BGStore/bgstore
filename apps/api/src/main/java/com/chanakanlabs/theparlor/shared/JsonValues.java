package com.chanakanlabs.theparlor.shared;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class JsonValues {

  private JsonValues() {
    // Utility class
  }

  public static Map<String, Object> immutableCopy(Map<String, Object> source) {

    var copy = new LinkedHashMap<String, Object>();

    source.forEach((key, value) -> copy.put(key, immutableValue(value)));

    return Collections.unmodifiableMap(copy);
  }

  private static Object immutableValue(Object value) {
    if (value instanceof Map<?, ?> map) {
      var copy = new LinkedHashMap<Object, Object>();

      map.forEach((key, nested) -> copy.put(key, immutableValue(nested)));

      return Collections.unmodifiableMap(copy);
    }

    if (value instanceof List<?> list) {
      return list.stream().map(JsonValues::immutableValue).toList();
    }

    return value;
  }
}
