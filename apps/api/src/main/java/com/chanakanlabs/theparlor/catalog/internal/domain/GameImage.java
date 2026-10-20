package com.chanakanlabs.theparlor.catalog.internal.domain;

import static com.chanakanlabs.theparlor.catalog.internal.CatalogLimits.*;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.UuidGenerator;

@Entity
@DynamicUpdate
@Table(name = "game_images")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GameImage {

  GameImage(Game game, String url, String altText, int sortOrder) {
    this.game = Objects.requireNonNull(game);
    update(url, altText);
    this.sortOrder = sortOrder;
  }

  void update(String url, String altText) {
    if (url == null
        || url.isBlank()
        || url.codePointCount(0, url.length()) > MAX_IMAGE_URL_LENGTH) {
      throw new IllegalArgumentException(
          "Image URL must be nonblank and at most " + MAX_IMAGE_URL_LENGTH + " characters");
    }
    if (altText != null
        && altText.codePointCount(0, altText.length()) > MAX_IMAGE_ALT_TEXT_LENGTH) {
      throw new IllegalArgumentException(
          "Image alt text must be at most " + MAX_IMAGE_ALT_TEXT_LENGTH + " characters");
    }
    this.url = url;
    this.altText = altText;
  }

  void detachFromGame() {
    this.game = null;
  }

  @Id
  @GeneratedValue
  @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "game_id", nullable = false)
  private Game game;

  @Column(nullable = false, columnDefinition = "TEXT")
  private String url;

  @Column(name = "alt_text", length = MAX_IMAGE_ALT_TEXT_LENGTH)
  private String altText;

  @Column(name = "sort_order", nullable = false)
  private int sortOrder;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;
}
