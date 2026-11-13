package com.chanakanlabs.theparlor.shared;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Opaque keyset cursors shared by every paginated list. A cursor is bound to its operation and
 * filters so it cannot be replayed against a different query; it carries position, never
 * authorization. Limits match the shared Limit and Cursor parameters of the API contract.
 */
public final class KeysetCursor {
  private KeysetCursor() {}

  /** Page size when the request omits limit. */
  public static final int DEFAULT_PAGE_SIZE = 20;

  /** Maximum requested page size. */
  public static final int MAX_PAGE_SIZE = 100;

  /** Maximum encoded cursor length in Java characters. */
  public static final int MAX_CURSOR_LENGTH = 2048;

  public static int limit(Integer limit) {
    int result = limit == null ? DEFAULT_PAGE_SIZE : limit;
    if (result < 1 || result > MAX_PAGE_SIZE)
      throw new IllegalArgumentException("Limit must be between 1 and " + MAX_PAGE_SIZE);
    return result;
  }

  /** Returns the position after which the next page starts, or null for the first page. */
  public static String after(String cursor, String operation, String filters) {
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

  /** As {@link #after}, for lists ordered by a UUID identifier. */
  public static UUID afterId(String cursor, String operation, String filters) {
    String key = after(cursor, operation, filters);
    if (key == null) return null;
    UUID id = UUID.fromString(key);
    if (!id.toString().equals(key)) throw new IllegalArgumentException("Invalid cursor ID");
    return id;
  }

  public static String next(String operation, String filters, Object id) {
    String value = "1|" + operation + "|" + digest(filters) + "|" + id;
    return Base64.getUrlEncoder()
        .withoutPadding()
        .encodeToString(value.getBytes(StandardCharsets.UTF_8));
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
