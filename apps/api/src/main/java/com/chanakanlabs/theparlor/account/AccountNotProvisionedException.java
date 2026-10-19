package com.chanakanlabs.theparlor.account;

public class AccountNotProvisionedException extends RuntimeException {
  public AccountNotProvisionedException() {
    super("Authenticated user has no account provisioned.");
  }
}
