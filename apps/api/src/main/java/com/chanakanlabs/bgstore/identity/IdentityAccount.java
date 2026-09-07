package com.chanakanlabs.bgstore.identity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/** The {@code identity_accounts} table, created from this class by {@code ddl-auto}. */
@Entity
@Table(name = "identity_accounts")
@SuppressWarnings("NullAway.Init")
class IdentityAccount {

  @Id
  @Column(name = "subject", nullable = false)
  private String subject;

  @Column(name = "username", nullable = false)
  private String username;

  @Column(name = "email", nullable = false)
  private String email;

  @Column(name = "first_name", nullable = false)
  private String firstName;

  @Column(name = "last_name", nullable = false)
  private String lastName;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private OffsetDateTime createdAt;

  @UpdateTimestamp
  @Column(name = "updated_at", nullable = false)
  private OffsetDateTime updatedAt;

  protected IdentityAccount() {}

  IdentityAccount(String subject) {
    this.subject = subject;
  }

  void apply(String username, String email, String firstName, String lastName) {
    this.username = username;
    this.email = email;
    this.firstName = firstName;
    this.lastName = lastName;
  }
}
