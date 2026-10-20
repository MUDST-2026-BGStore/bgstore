package com.chanakanlabs.theparlor.catalog.internal.domain;

import static com.chanakanlabs.theparlor.catalog.internal.CatalogLimits.MAX_TAXONOMY_KEY_LENGTH;

/** Immutable key rules shared by categories and mechanics, independent of search filter rules. */
final class TaxonomyKey {
  private TaxonomyKey() {}

  static void validate(String name) {
    if (name == null
        || name.isBlank()
        || name.codePointCount(0, name.length()) > MAX_TAXONOMY_KEY_LENGTH) {
      throw new IllegalArgumentException(
          "Taxonomy name must be nonblank and at most " + MAX_TAXONOMY_KEY_LENGTH + " characters");
    }
  }
}
