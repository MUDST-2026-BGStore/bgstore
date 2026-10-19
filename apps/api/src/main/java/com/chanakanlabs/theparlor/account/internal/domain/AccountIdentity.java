package com.chanakanlabs.theparlor.account.internal.domain;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.Getter;

@Entity
@Table(name = "account_identities")
@IdClass(AccountIdentityId.class)
public class AccountIdentity {

  @Id
  @Column(nullable = false)
  private String issuer;

  @Id
  @Column(nullable = false, length = 255)
  private String subject;

  @Getter
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "account_id", nullable = false)
  private Account account;

  @Column(name = "linked_at", nullable = false, insertable = false, updatable = false)
  private Instant linkedAt;

  protected AccountIdentity() {}

  public AccountIdentity(String issuer, String subject, Account account) {
    this.issuer = issuer;
    this.subject = subject;
    this.account = account;
  }
}
