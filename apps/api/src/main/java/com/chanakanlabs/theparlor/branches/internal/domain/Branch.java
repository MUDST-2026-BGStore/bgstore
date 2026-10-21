package com.chanakanlabs.theparlor.branches.internal.domain;

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
@Table(name = "branches")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Branch {
  /** Maximum branch name length. */
  public static final int MAX_NAME_LENGTH = 120;

  /** Maximum branch address length; independent of audit reason limits. */
  public static final int MAX_ADDRESS_LENGTH = 500;

  /** Maximum branch contact phone length; differs from account/customer storage. */
  public static final int MAX_PHONE_LENGTH = 40;

  @Id
  @GeneratedValue
  @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
  private UUID id;

  @Column(nullable = false, length = MAX_NAME_LENGTH)
  private String name;

  @Column(nullable = false, length = MAX_ADDRESS_LENGTH)
  private String address;

  @Column(name = "phone_number", length = MAX_PHONE_LENGTH)
  private String phoneNumber;

  @Column(nullable = false)
  private boolean active;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @UpdateTimestamp
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;
}
