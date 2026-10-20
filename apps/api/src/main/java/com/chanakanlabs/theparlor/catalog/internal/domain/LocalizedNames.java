package com.chanakanlabs.theparlor.catalog.internal.domain;

import static com.chanakanlabs.theparlor.catalog.internal.CatalogLimits.*;

import java.util.*;

/** Validation shared by the catalog's two localized reference vocabularies. */
final class LocalizedNames {
  private LocalizedNames() {}

  static Map<String, String> validate(Map<String, String> names) {
    if (names == null) throw new IllegalArgumentException("Localized names are required");
    var result = new LinkedHashMap<String, String>();
    names.forEach(
        (tag, text) -> {
          if (tag == null || tag.isBlank() || tag.length() > MAX_LOCALE_TAG_LENGTH) {
            throw new IllegalArgumentException(
                "Locale must contain 1–" + MAX_LOCALE_TAG_LENGTH + " characters");
          }
          String canonical;
          try {
            canonical = new Locale.Builder().setLanguageTag(tag).build().toLanguageTag();
          } catch (IllformedLocaleException exception) {
            throw new IllegalArgumentException("Invalid BCP 47 locale", exception);
          }
          if (canonical.length() > MAX_LOCALE_TAG_LENGTH)
            throw new IllegalArgumentException(
                "Locale exceeds " + MAX_LOCALE_TAG_LENGTH + " characters");
          if (text == null
              || text.isBlank()
              || text.codePointCount(0, text.length()) > MAX_LOCALIZED_NAME_LENGTH) {
            throw new IllegalArgumentException(
                "Localized name must contain 1–" + MAX_LOCALIZED_NAME_LENGTH + " characters");
          }
          if (result.putIfAbsent(canonical, text) != null) {
            throw new IllegalArgumentException("Duplicate locale: " + canonical);
          }
        });
    if (!result.containsKey(REQUIRED_LOCALE))
      throw new IllegalArgumentException("Name is required for locale " + REQUIRED_LOCALE);
    return result;
  }
}
