package com.chanakanlabs.bgstore.identity;

import org.jooq.DSLContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IdentityAccountService {

  private final DSLContext database;

  public IdentityAccountService(DSLContext database) {
    this.database = database;
  }

  @Transactional
  public void synchronize(AuthenticatedIdentity identity) {
    database.execute(
        """
        INSERT INTO identity_accounts (subject, username, email, first_name, last_name)
        VALUES (?, ?, ?, ?, ?)
        ON CONFLICT (subject) DO UPDATE
        SET username = EXCLUDED.username,
            email = EXCLUDED.email,
            first_name = EXCLUDED.first_name,
            last_name = EXCLUDED.last_name,
            updated_at = CURRENT_TIMESTAMP
        """,
        identity.subject(),
        identity.username(),
        identity.email(),
        identity.firstName(),
        identity.lastName());
  }
}
