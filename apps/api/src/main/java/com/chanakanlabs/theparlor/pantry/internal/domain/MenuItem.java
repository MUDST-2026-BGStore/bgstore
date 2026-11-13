package com.chanakanlabs.theparlor.pantry.internal.domain;

import com.chanakanlabs.theparlor.shared.EnumStorageLengths;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

/** A food or drink item on the business-wide menu. Written by the reference data seed. */
@Entity
@DynamicUpdate
@Table(name = "menu_items")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MenuItem {
  /** The locale every name map must contain, used for order line snapshots. */
  public static final String DEFAULT_LOCALE = "en-US";

  @Id private UUID id;

  @Column(nullable = false, updatable = false, length = 60)
  private String sku;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = EnumStorageLengths.STATUS)
  private MenuCategory category;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(nullable = false, columnDefinition = "jsonb")
  private Map<String, String> names;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(nullable = false, columnDefinition = "jsonb")
  private Map<String, String> descriptions;

  @Column(name = "price_minor", nullable = false)
  private long priceMinor;

  @Column(length = 40)
  private String dietary;

  @Column(nullable = false)
  private boolean popular;

  @Column(nullable = false)
  private boolean active;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @UpdateTimestamp
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  public String defaultName() {
    return names.get(DEFAULT_LOCALE);
  }
}
