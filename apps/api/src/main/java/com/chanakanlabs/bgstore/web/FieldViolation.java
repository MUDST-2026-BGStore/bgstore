package com.chanakanlabs.bgstore.web;

/**
 * One rejected request property.
 *
 * @param field dotted path of the property, matching the request body shape (for example {@code
 *     copies[0].copies})
 * @param message message key the browser resolves through {@code games.form.errors.*}
 */
public record FieldViolation(String field, String message) {

  /** Rejection reason for a property the payload left out or blank. */
  public static final String REQUIRED = "required";

  /** Rejection reason for a property that is present but outside its allowed range or length. */
  public static final String INVALID = "invalid";
}
