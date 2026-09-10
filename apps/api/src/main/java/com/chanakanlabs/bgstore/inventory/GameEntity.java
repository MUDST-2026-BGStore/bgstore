package com.chanakanlabs.bgstore.inventory;

import com.chanakanlabs.bgstore.contract.model.GameCategory;
import com.chanakanlabs.bgstore.contract.model.GameLifecycle;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;
import org.springframework.lang.Nullable;

/** JPA mapping for the migration-owned {@code game} table. */
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

  // The columns below were added after the table first shipped. `ddl-auto`
  // cannot add a NOT NULL column to a table that already holds rows, so they
  // are nullable in the schema and an absent value reads as "none".

  @JdbcTypeCode(SqlTypes.ARRAY)
  @Column(name = "image_urls")
  private @Nullable String[] imageUrls;

  @Column(name = "goal_en", length = 600)
  private @Nullable String goalEn;

  @Column(name = "goal_th", length = 600)
  private @Nullable String goalTh;

  @Column(name = "players_note_en", length = 600)
  private @Nullable String playersNoteEn;

  @Column(name = "players_note_th", length = 600)
  private @Nullable String playersNoteTh;

  @Column(name = "equipment_en", length = 600)
  private @Nullable String equipmentEn;

  @Column(name = "equipment_th", length = 600)
  private @Nullable String equipmentTh;

  @ElementCollection
  @CollectionTable(name = "game_guide_step", joinColumns = @JoinColumn(name = "game_id"))
  @OrderColumn(name = "position")
  private List<PlayGuideStepColumns> guideSteps = new ArrayList<>();

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
    imageUrls = command.imageUrls().toArray(String[]::new);
    applyGuide(command.guide());
    lifecycle = command.lifecycle().getValue();
  }

  private void applyGuide(PlayGuide guide) {
    goalEn = guide.goal().english();
    goalTh = guide.goal().thai();
    playersNoteEn = guide.players().english();
    playersNoteTh = guide.players().thai();
    equipmentEn = guide.equipment().english();
    equipmentTh = guide.equipment().thai();
    // Cleared and refilled in place: Hibernate tracks the collection instance,
    // so replacing it would orphan the rows it already manages.
    guideSteps.clear();
    guide.steps().forEach(step -> guideSteps.add(new PlayGuideStepColumns(step)));
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
        imageUrls == null ? List.of() : List.of(imageUrls),
        new PlayGuide(
            new LocalizedText(goalEn, goalTh),
            new LocalizedText(playersNoteEn, playersNoteTh),
            new LocalizedText(equipmentEn, equipmentTh),
            guideSteps.stream().map(PlayGuideStepColumns::toStep).toList()),
        GameLifecycle.fromValue(lifecycle),
        createdAt,
        lastPlayedAt);
  }
}
