package com.chanakanlabs.theparlor.catalog.internal.application;

import static com.chanakanlabs.theparlor.catalog.internal.CatalogLimits.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;

/** Operation/filter-bound keyset cursors. They carry position, never authorization. */
final class CatalogCursor {
  private CatalogCursor() {}

  static int limit(Integer limit) {
    int result = limit == null ? DEFAULT_PAGE_SIZE : limit;
    if (result < 1 || result > MAX_PAGE_SIZE)
      throw new IllegalArgumentException("Limit must be between 1 and " + MAX_PAGE_SIZE);
    return result;
  }

  static String after(String cursor, String operation, String filters) {
    if (cursor == null) return null;
    if (cursor.isBlank() || cursor.length() > MAX_CURSOR_LENGTH)
      throw new IllegalArgumentException("Invalid cursor");
    String[] parts =
        new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8).split("\\|", -1);
    if (parts.length != 4
        || !parts[0].equals("1")
        || !parts[1].equals(operation)
        || !parts[2].equals(digest(filters))
        || parts[3].isEmpty()) {
      throw new IllegalArgumentException("Invalid cursor or filters differ from the previous page");
    }
    return parts[3];
  }

  static String next(String operation, String filters, Object id) {
    String value = "1|" + operation + "|" + digest(filters) + "|" + id;
    return Base64.getUrlEncoder()
        .withoutPadding()
        .encodeToString(value.getBytes(StandardCharsets.UTF_8));
  }

  static String gameFilters(GameSearchCriteria filters) {
    var fields = new ArrayList<String>();
    fields.add(filters.q());
    fields.add(Integer.toString(filters.categories().size()));
    fields.addAll(filters.categories());
    fields.add(Integer.toString(filters.mechanics().size()));
    fields.addAll(filters.mechanics());
    fields.add(filters.difficulty() == null ? null : filters.difficulty().name());
    fields.add(filters.players() == null ? null : filters.players().toString());
    var value = new StringBuilder();
    for (String field : fields) value.append(field == null ? "-1:" : field.length() + ":" + field);
    return value.toString();
  }

  private static String digest(String value) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 is required by the Java runtime", exception);
    }
  }
}
