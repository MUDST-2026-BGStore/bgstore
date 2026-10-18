package com.chanakanlabs.theparlor.shared;

/** Text limits shared by order and reservation workflows. */
public final class WorkflowTextLimits {
  private WorkflowTextLimits() {}

  /** Maximum order and reservation notes length, matching their request schemas. */
  public static final int MAX_NOTES_LENGTH = 1000;

  /** Maximum cancellation reason length, matching the shared ReasonRequest contract. */
  public static final int MAX_CANCELLATION_REASON_LENGTH = 500;
}
