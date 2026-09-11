package com.chanakanlabs.bgstore.identity;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IdentityAccountService {

  private final IdentityAccountJpaRepository accounts;

  IdentityAccountService(IdentityAccountJpaRepository accounts) {
    this.accounts = accounts;
  }

  /**
   * Upserts the account. Without a database-side {@code ON CONFLICT}, the read and the write are
   * one transaction so two concurrent sign-ins cannot both insert.
   */
  @Transactional
  public void synchronize(AuthenticatedIdentity identity) {
    var account =
        accounts
            .findById(identity.subject())
            .orElseGet(() -> new IdentityAccount(identity.subject()));
    account.apply(identity.username(), identity.email(), identity.firstName(), identity.lastName());
    accounts.save(account);
  }
}
