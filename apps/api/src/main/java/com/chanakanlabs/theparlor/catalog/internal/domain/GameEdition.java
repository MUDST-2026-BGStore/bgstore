package com.chanakanlabs.theparlor.catalog.internal.domain;

import static com.chanakanlabs.theparlor.catalog.internal.CatalogLimits.*;

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
@Table(name = "game_editions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GameEdition {
  @Id
  @GeneratedValue
  @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
  private UUID id;

  @Version private Long version;

  @Column(name = "game_id", nullable = false)
  private UUID gameId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private EditionKind kind;

  @Column(name = "language_code", nullable = false, length = MAX_LOCALE_TAG_LENGTH)
  private String languageCode;

  @Column(nullable = false)
  private boolean active;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @UpdateTimestamp
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  public static GameEdition create(
      UUID gameId, EditionKind kind, String languageCode, boolean active) {
    var edition = new GameEdition();
    edition.gameId = java.util.Objects.requireNonNull(gameId);
    edition.update(kind, languageCode, active);
    return edition;
  }

  public void update(EditionKind kind, String languageCode, boolean active) {
    java.util.Objects.requireNonNull(kind);
    if (languageCode == null
        || languageCode.isBlank()
        || languageCode.length() > MAX_LOCALE_TAG_LENGTH) {
      throw new IllegalArgumentException(
          "Edition language must contain 1–" + MAX_LOCALE_TAG_LENGTH + " characters");
    }
    String canonical;
    try {
      canonical =
          new java.util.Locale.Builder().setLanguageTag(languageCode).build().toLanguageTag();
    } catch (java.util.IllformedLocaleException exception) {
      throw new IllegalArgumentException("Invalid edition language tag", exception);
    }
    if (canonical.length() > MAX_LOCALE_TAG_LENGTH)
      throw new IllegalArgumentException(
          "Edition language exceeds " + MAX_LOCALE_TAG_LENGTH + " characters");
    this.kind = kind;
    this.languageCode = canonical;
    this.active = active;
  }
}
