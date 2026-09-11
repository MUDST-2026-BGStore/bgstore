package com.chanakanlabs.bgstore.clients;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** CRUD over {@link ClientProfileRecord}; Spring Data supplies the implementation. */
interface ClientProfileJpaRepository extends JpaRepository<ClientProfileRecord, String> {

  /** Creates the incomplete profile without a concurrent first-request race. */
  @Modifying
  @Query(
      value =
          "INSERT INTO client_profiles (subject) VALUES (:subject) ON CONFLICT (subject) DO NOTHING",
      nativeQuery = true)
  void insertIfAbsent(@Param("subject") String subject);
}
