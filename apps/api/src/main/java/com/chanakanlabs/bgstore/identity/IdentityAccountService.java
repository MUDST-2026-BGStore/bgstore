package com.chanakanlabs.bgstore.identity;

import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IdentityAccountService implements ClientDirectory {

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

  @Override
  @Transactional(readOnly = true)
  public boolean existsClient(String subject) {
    return accounts.existsBySubjectAndApplicationRole(subject, "CLIENT");
  }

  @Override
  @Transactional(readOnly = true)
  public List<ClientLookup> findClients(@Nullable String search) {
    return accounts.findClients(search);
  }
}
