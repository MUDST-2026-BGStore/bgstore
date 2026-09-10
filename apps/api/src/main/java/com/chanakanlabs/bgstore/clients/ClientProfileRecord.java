package com.chanakanlabs.bgstore.clients;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.springframework.lang.Nullable;

/**
 * JPA mapping for the migration-owned {@code client_profiles} table.
 *
 * <p>Named {@code ...Record} rather than {@code ClientProfile} because the contract model this
 * module answers with already owns that name.
 */
@Entity
@Table(name = "client_profiles")
@SuppressWarnings("NullAway.Init")
class ClientProfileRecord {

  @Id
  @Column(name = "subject", nullable = false)
  private String subject;

  // 16 characters is the longest E.164 number: '+' plus up to 15 digits.
  @Column(name = "phone_e164", length = 16)
  private @Nullable String phoneE164;

  @Column(name = "completed_at")
  private @Nullable OffsetDateTime completedAt;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private OffsetDateTime createdAt;

  @UpdateTimestamp
  @Column(name = "updated_at", nullable = false)
  private OffsetDateTime updatedAt;

  protected ClientProfileRecord() {}

  ClientProfileRecord(String subject) {
    this.subject = subject;
  }

  void complete(String phoneE164) {
    this.phoneE164 = phoneE164;
    this.completedAt = OffsetDateTime.now(ZoneOffset.UTC);
  }

  ClientProfileData toData() {
    return new ClientProfileData(phoneE164, completedAt != null);
  }
}
