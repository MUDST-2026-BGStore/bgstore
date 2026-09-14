package com.chanakanlabs.bgstore.identity;

import org.jspecify.annotations.Nullable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.NativeQuery;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** CRUD over {@link IdentityAccount}; Spring Data supplies the implementation. */
public interface IdentityAccountJpaRepository extends JpaRepository<IdentityAccount, String> {

  record ClientLookup(
      String subject,
      String displayName,
      @Nullable String firstName,
      @Nullable String lastName,
      @Nullable String phone) {}

  /** Synchronizes the OIDC projection without a read-then-insert race on first sign-in. */
  @Modifying
  @NativeQuery(
      value =
          """
          INSERT INTO identity_accounts (subject, username, email, first_name, last_name, application_role)
          VALUES (:subject, :username, :email, :firstName, :lastName, :applicationRole)
          ON CONFLICT (subject) DO UPDATE SET
              username = EXCLUDED.username,
              email = EXCLUDED.email,
              first_name = EXCLUDED.first_name,
              last_name = EXCLUDED.last_name,
              application_role = EXCLUDED.application_role,
              updated_at = CURRENT_TIMESTAMP
          """)
  void upsert(
      @Param("subject") String subject,
      @Param("username") String username,
      @Param("email") String email,
      @Param("firstName") String firstName,
      @Param("lastName") String lastName,
      @Param("applicationRole") String applicationRole);

  @Query(
      "select a from IdentityAccount a where a.applicationRole in ('STAFF', 'MANAGER') order by a.username")
  java.util.List<IdentityAccount> findOperationalAccounts();

  boolean existsBySubjectAndApplicationRole(String subject, String applicationRole);

  @NativeQuery(
      value =
          """
          SELECT a.subject AS subject,
                 COALESCE(NULLIF(trim(CONCAT(COALESCE(p.first_name, a.first_name), ' ', COALESCE(p.last_name, a.last_name))), ''), a.username) AS displayName,
                 p.first_name AS firstName,
                 p.last_name AS lastName,
                 p.phone_e164 AS phone
          FROM identity_accounts a
          LEFT JOIN client_profiles p ON p.subject = a.subject
          WHERE a.application_role = 'CLIENT'
            AND (:search IS NULL OR :search = ''
                 OR lower(a.username) LIKE lower(concat('%', :search, '%'))
                 OR lower(a.email) LIKE lower(concat('%', :search, '%'))
                 OR lower(COALESCE(p.first_name, a.first_name)) LIKE lower(concat('%', :search, '%'))
                 OR lower(COALESCE(p.last_name, a.last_name)) LIKE lower(concat('%', :search, '%'))
                 OR COALESCE(p.phone_e164, '') LIKE concat('%', :search, '%'))
          ORDER BY displayName
          LIMIT 50
          """)
  java.util.List<ClientLookup> findClients(@Param("search") @Nullable String search);
}
