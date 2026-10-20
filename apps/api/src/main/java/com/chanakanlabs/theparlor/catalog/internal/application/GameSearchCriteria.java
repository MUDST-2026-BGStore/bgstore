package com.chanakanlabs.theparlor.catalog.internal.application;

import static com.chanakanlabs.theparlor.catalog.internal.CatalogLimits.*;

import com.chanakanlabs.theparlor.catalog.internal.domain.GameDifficulty;
import java.util.*;

/** Validated, canonical catalog search input; independent of database query construction. */
public record GameSearchCriteria(
    String q,
    List<String> categories,
    List<String> mechanics,
    GameDifficulty difficulty,
    Integer players) {
  public GameSearchCriteria {
    if (q != null && (q.isEmpty() || q.codePointCount(0, q.length()) > MAX_SEARCH_TEXT_LENGTH)) {
      throw new IllegalArgumentException(
          "Search text must contain 1–" + MAX_SEARCH_TEXT_LENGTH + " characters");
    }
    q = q == null ? null : q.toLowerCase(Locale.ROOT);
    categories = keys(categories);
    mechanics = keys(mechanics);
    if (players != null && players < 1)
      throw new IllegalArgumentException("Players must be positive");
  }

  private static List<String> keys(List<String> keys) {
    if (keys == null) return List.of();
    if (keys.size() > MAX_FILTER_VALUES)
      throw new IllegalArgumentException(
          "At most " + MAX_FILTER_VALUES + " filter values are allowed");
    for (var key : keys) {
      if (key == null
          || key.isEmpty()
          || key.codePointCount(0, key.length()) > MAX_TAXONOMY_KEY_LENGTH) {
        throw new IllegalArgumentException(
            "Taxonomy filter keys must contain 1–" + MAX_TAXONOMY_KEY_LENGTH + " characters");
      }
    }
    return keys.stream().distinct().sorted().toList();
  }
}
