package com.chanakanlabs.theparlor.customers.internal.domain;

import com.chanakanlabs.theparlor.shared.ContactStorageLimits;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;

@Entity
@DynamicUpdate
@Table(name = "customers")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Customer {
  @Id
  @GeneratedValue
  @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
  private UUID id;

  @Column(name = "account_id", unique = true)
  private UUID accountId;

  @Column(name = "contact_name")
  private String contactName;

  @Column(name = "contact_phone", length = ContactStorageLimits.MAX_PHONE_LENGTH)
  private String contactPhone;

  @Column(name = "contact_email")
  private String contactEmail;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @UpdateTimestamp
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;
}
