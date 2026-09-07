package com.chanakanlabs.bgstore.clients;

import org.springframework.data.jpa.repository.JpaRepository;

/** CRUD over {@link ClientProfileRecord}; Spring Data supplies the implementation. */
interface ClientProfileJpaRepository extends JpaRepository<ClientProfileRecord, String> {}
