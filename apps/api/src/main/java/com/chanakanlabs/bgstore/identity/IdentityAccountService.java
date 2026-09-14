package com.chanakanlabs.bgstore.identity;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IdentityAccountService {

  private final IdentityAccountJpaRepository accounts;

  IdentityAccountService(IdentityAccountJpaRepository accounts) {
    this.accounts = accounts;
  }

  /** Upserts the account atomically so concurrent first sign-ins cannot race on the subject key. */
  @Transactional
  public void synchronize(AuthenticatedIdentity identity) {
    accounts.upsert(
        identity.subject(),
        identity.username(),
        identity.email(),
        identity.firstName(),
        identity.lastName(),
        identity.roles().contains(ApplicationRole.MANAGER)
            ? "MANAGER"
            : identity.roles().contains(ApplicationRole.STAFF) ? "STAFF" : "CLIENT");
  }
}
