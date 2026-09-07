package com.chanakanlabs.bgstore.identity;

import org.springframework.data.jpa.repository.JpaRepository;

/** CRUD over {@link IdentityAccount}; Spring Data supplies the implementation. */
interface IdentityAccountJpaRepository extends JpaRepository<IdentityAccount, String> {}
