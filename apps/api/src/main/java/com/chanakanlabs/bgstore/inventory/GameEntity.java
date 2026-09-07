package com.chanakanlabs.bgstore.inventory;

import com.chanakanlabs.bgstore.contract.model.GameCategory;
import com.chanakanlabs.bgstore.contract.model.GameLifecycle;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;
import org.springframework.lang.Nullable;

/** The {@code game} table, created from this class by {@code ddl-auto}. */
@Entity
@Table(name = "game")
@SuppressWarnings("NullAway.Init")
class GameEntity {

  @Id
  @Column(name = "id", nullable = false)
  private UUID id;

  @Column(name = "title_en", nullable = false, length = 120)
  private String titleEn;

  @Column(name = "title_th", length = 120)
  private @Nullable String titleTh;

  @Column(name = "description_en", length = 160)
  private @Nullable String descriptionEn;

  @Column(name = "description_th", length = 160)
  private @Nullable String descriptionTh;

  @Column(name = "category", nullable = false, length = 16)
  private String category;

  @Column(name = "min_players", nullable = false)
  private int minPlayers;

  @Column(name = "max_players", nullable = false)
  private int maxPlayers;

  @Column(name = "play_time_minutes")
  private @Nullable Integer playTimeMinutes;

  @Column(name = "difficulty", length = 60)
  private @Nullable String difficulty;

  @JdbcTypeCode(SqlTypes.ARRAY)
  @Column(name = "tags", nullable = false)
  private String[] tags = new String[0];

  @Column(name = "lifecycle", nullable = false, length = 16)
  private String lifecycle = GameLifecycle.ACTIVE.getValue();

  /** Owned by the play-session module once it exists; null until a session has used this game. */
  @Column(name = "last_played_at")
  private @Nullable OffsetDateTime lastPlayedAt;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private OffsetDateTime createdAt;

  @UpdateTimestamp
  @Column(name = "updated_at", nullable = false)
  private OffsetDateTime updatedAt;

  protected GameEntity() {}

  GameEntity(UUID id) {
    this.id = id;
  }

  /** The fields a create and a replace both write, so the two cannot drift apart. */
  void apply(GameCommand command) {
    titleEn = Objects.requireNonNull(command.title().english());
    titleTh = command.title().thai();
    descriptionEn = command.description().english();
    descriptionTh = command.description().thai();
    category = command.category().getValue();
    minPlayers = command.minPlayers();
    maxPlayers = command.maxPlayers();
    playTimeMinutes = command.playTimeMinutes();
    difficulty = command.difficulty();
    tags = command.tags().toArray(String[]::new);
    lifecycle = command.lifecycle().getValue();
  }

  void retire() {
    lifecycle = GameLifecycle.RETIRED.getValue();
  }

  StoredGame toStoredGame() {
    return new StoredGame(
        id,
        new LocalizedText(titleEn, titleTh),
        new LocalizedText(descriptionEn, descriptionTh),
        GameCategory.fromValue(category),
        minPlayers,
        maxPlayers,
        playTimeMinutes,
        difficulty,
        List.of(tags),
        GameLifecycle.fromValue(lifecycle),
        createdAt,
        lastPlayedAt);
  }
}
