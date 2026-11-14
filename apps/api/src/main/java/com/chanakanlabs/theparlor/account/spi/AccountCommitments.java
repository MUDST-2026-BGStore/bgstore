package com.chanakanlabs.theparlor.account.spi;

/**
 * Implemented by modules holding a customer's open commitments; asked before an account is deleted.
 */
public interface AccountCommitments {
  /** subject is the customer's OIDC subject. */
  boolean hasOpenCommitments(String subject);
}
