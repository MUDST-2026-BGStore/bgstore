package com.chanakanlabs.bgstore.web;

import java.io.Serial;

/** Raised when a path addresses a record that does not exist. Answered with 404. */
public class ResourceNotFoundException extends RuntimeException {

  @Serial private static final long serialVersionUID = 1L;

  public ResourceNotFoundException(String resource, Object identifier) {
    super(resource + " " + identifier + " does not exist");
  }
}
