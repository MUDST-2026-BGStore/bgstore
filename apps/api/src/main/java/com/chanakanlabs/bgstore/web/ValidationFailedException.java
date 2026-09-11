package com.chanakanlabs.bgstore.web;

import java.io.Serial;
import java.util.List;

/**
 * Raised when a payload parses but breaks a rule the schema cannot express, such as a player range
 * whose maximum is below its minimum or a branch that does not exist.
 *
 * <p>Answered with 422 and the full list of violations so a form can mark every bad field at once.
 */
public class ValidationFailedException extends RuntimeException {

  @Serial private static final long serialVersionUID = 1L;

  private final transient List<FieldViolation> violations;

  public ValidationFailedException(List<FieldViolation> violations) {
    super("Request violated " + violations.size() + " rule(s)");
    this.violations = List.copyOf(violations);
  }

  public List<FieldViolation> violations() {
    return violations;
  }
}
