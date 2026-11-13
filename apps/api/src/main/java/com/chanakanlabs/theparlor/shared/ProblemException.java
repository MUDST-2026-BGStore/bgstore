package com.chanakanlabs.theparlor.shared;

import org.springframework.http.HttpStatus;

/**
 * An application failure with its own stable error code in the contract's Problem body. Use
 * ResponseStatusException when the generic code for the status is enough.
 */
public class ProblemException extends RuntimeException {
  private final HttpStatus status;
  private final String code;

  public ProblemException(HttpStatus status, String code, String message) {
    super(message);
    this.status = java.util.Objects.requireNonNull(status);
    this.code = java.util.Objects.requireNonNull(code);
  }

  public HttpStatus status() {
    return status;
  }

  public String code() {
    return code;
  }
}
