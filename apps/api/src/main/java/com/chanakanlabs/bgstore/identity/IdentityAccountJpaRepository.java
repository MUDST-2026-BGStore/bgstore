package com.chanakanlabs.bgstore.identity;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** CRUD over {@link IdentityAccount}; Spring Data supplies the implementation. */
interface IdentityAccountJpaRepository extends JpaRepository<IdentityAccount, String> {

  /** Synchronizes the OIDC projection without a read-then-insert race on first sign-in. */
  @Modifying
  @Query(
      value =
          """
          INSERT INTO identity_accounts (subject, username, email, first_name, last_name)
          VALUES (:subject, :username, :email, :firstName, :lastName)
          ON CONFLICT (subject) DO UPDATE SET
              username = EXCLUDED.username,
              email = EXCLUDED.email,
              first_name = EXCLUDED.first_name,
              last_name = EXCLUDED.last_name,
              updated_at = CURRENT_TIMESTAMP
          """,
      nativeQuery = true)
  void upsert(
      @Param("subject") String subject,
      @Param("username") String username,
      @Param("email") String email,
      @Param("firstName") String firstName,
      @Param("lastName") String lastName);
}
