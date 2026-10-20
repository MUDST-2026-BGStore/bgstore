package com.chanakanlabs.theparlor.catalog.internal.domain;

import static com.chanakanlabs.theparlor.catalog.internal.CatalogLimits.*;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;

@Entity
@DynamicUpdate
@Table(name = "games")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Game {
  @Id
  @GeneratedValue
  @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
  private UUID id;

  @Version private Long version;

  @Column(nullable = false, length = MAX_GAME_NAME_LENGTH)
  private String name;

  @Column(length = MAX_DESIGNER_LENGTH)
  private String designer;

  @Enumerated(EnumType.STRING)
  @Column(length = 20)
  private GameDifficulty difficulty;

  @Column(name = "min_players", nullable = false)
  private int minPlayers;

  @Column(name = "max_players", nullable = false)
  private int maxPlayers;

  @Column(name = "min_age")
  private Integer minAge;

  @Column(name = "play_minutes_min", nullable = false)
  private int playMinutesMin;

  @Column(name = "play_minutes_max", nullable = false)
  private int playMinutesMax;

  @Column(name = "teach_minutes")
  private Integer teachMinutes;

  @Column(name = "setup_minutes")
  private Integer setupMinutes;

  @Column(nullable = false)
  private boolean active;

  // Categories: many-to-many
  @ManyToMany(fetch = FetchType.LAZY)
  @JoinTable(
      name = "game_category_links",
      joinColumns = @JoinColumn(name = "game_id"),
      inverseJoinColumns = @JoinColumn(name = "category_id"))
  @Getter(AccessLevel.NONE)
  @BatchSize(size = COLLECTION_BATCH_SIZE)
  private Set<GameCategory> categories = new HashSet<>();

  // Mechanics: many-to-many
  @ManyToMany(fetch = FetchType.LAZY)
  @JoinTable(
      name = "game_mechanic_links",
      joinColumns = @JoinColumn(name = "game_id"),
      inverseJoinColumns = @JoinColumn(name = "mechanic_id"))
  @Getter(AccessLevel.NONE)
  @BatchSize(size = COLLECTION_BATCH_SIZE)
  private Set<GameMechanic> mechanics = new HashSet<>();

  @ElementCollection(fetch = FetchType.LAZY)
  @CollectionTable(name = "game_descriptions", joinColumns = @JoinColumn(name = "game_id"))
  @MapKeyColumn(name = "locale", length = MAX_LOCALE_TAG_LENGTH, nullable = false)
  @Column(name = "description", nullable = false, columnDefinition = "TEXT")
  @Getter(AccessLevel.NONE)
  @BatchSize(size = COLLECTION_BATCH_SIZE)
  private Map<String, String> descriptions = new HashMap<>();

  @OneToMany(
      mappedBy = "game",
      cascade = CascadeType.ALL,
      orphanRemoval = true,
      fetch = FetchType.LAZY)
  @OrderBy("sortOrder ASC")
  @Getter(AccessLevel.NONE)
  @BatchSize(size = COLLECTION_BATCH_SIZE)
  private List<GameImage> images = new ArrayList<>();

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @UpdateTimestamp
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  /** Validates a complete set of scalar attributes before changing managed state. */
  public record Details(
      String name,
      String designer,
      GameDifficulty difficulty,
      int minPlayers,
      int maxPlayers,
      Integer minAge,
      int playMinutesMin,
      int playMinutesMax,
      Integer teachMinutes,
      Integer setupMinutes) {
    public Details {
      requireText(name, MAX_GAME_NAME_LENGTH, "name");
      if (designer != null) requireText(designer, MAX_DESIGNER_LENGTH, "designer");
      if (minPlayers < 1 || maxPlayers < minPlayers) {
        throw new IllegalArgumentException("Players must be positive and maxPlayers >= minPlayers");
      }
      if (playMinutesMin < 1 || playMinutesMax < playMinutesMin) {
        throw new IllegalArgumentException("Playtime must be positive and max >= min");
      }
      requireNonnegative(minAge, "minAge");
      requireNonnegative(teachMinutes, "teachMinutes");
      requireNonnegative(setupMinutes, "setupMinutes");
    }
  }

  public static Game create(
      Details details,
      Collection<GameCategory> categories,
      Collection<GameMechanic> mechanics,
      Map<String, String> descriptions,
      boolean active) {
    var game = new Game();
    game.updateDetails(details);
    Objects.requireNonNull(categories).forEach(game::addCategory);
    if (game.categories.isEmpty())
      throw new IllegalArgumentException("At least one category is required");
    Objects.requireNonNull(mechanics).forEach(game::addMechanic);
    Objects.requireNonNull(descriptions).forEach(game::putDescription);
    if (!game.descriptions.containsKey(REQUIRED_LOCALE)) {
      throw new IllegalArgumentException("Description is required for locale " + REQUIRED_LOCALE);
    }
    game.active = active;
    return game;
  }

  public void updateDetails(Details details) {
    Objects.requireNonNull(details);
    name = details.name();
    designer = details.designer();
    difficulty = details.difficulty();
    minPlayers = details.minPlayers();
    maxPlayers = details.maxPlayers();
    minAge = details.minAge();
    playMinutesMin = details.playMinutesMin();
    playMinutesMax = details.playMinutesMax();
    teachMinutes = details.teachMinutes();
    setupMinutes = details.setupMinutes();
  }

  public void deactivate() {
    active = false;
  }

  public void activate() {
    active = true;
  }

  public Set<GameCategory> getCategories() {
    return Collections.unmodifiableSet(categories);
  }

  public Set<GameMechanic> getMechanics() {
    return Collections.unmodifiableSet(mechanics);
  }

  public Map<String, String> getDescriptions() {
    return Collections.unmodifiableMap(descriptions);
  }

  public List<GameImage> getImages() {
    return Collections.unmodifiableList(images);
  }

  public void addCategory(GameCategory category) {
    Objects.requireNonNull(category);
    if (!categories.contains(category) && categories.size() >= MAX_CATEGORIES) {
      throw new IllegalArgumentException("At most " + MAX_CATEGORIES + " categories are allowed");
    }
    categories.add(category);
  }

  public void removeCategory(GameCategory category) {
    Objects.requireNonNull(category);
    if (categories.contains(category) && categories.size() == 1) {
      throw new IllegalArgumentException("At least one category is required");
    }
    categories.remove(category);
  }

  public void addMechanic(GameMechanic mechanic) {
    Objects.requireNonNull(mechanic);
    if (!mechanics.contains(mechanic) && mechanics.size() >= MAX_MECHANICS) {
      throw new IllegalArgumentException("At most " + MAX_MECHANICS + " mechanics are allowed");
    }
    mechanics.add(mechanic);
  }

  public void removeMechanic(GameMechanic mechanic) {
    mechanics.remove(Objects.requireNonNull(mechanic));
  }

  public void replaceCategories(Collection<GameCategory> values) {
    var validated = new LinkedHashSet<>(Objects.requireNonNull(values));
    if (validated.isEmpty() || validated.size() > MAX_CATEGORIES || validated.contains(null)) {
      throw new IllegalArgumentException(
          "Between 1 and " + MAX_CATEGORIES + " categories are required");
    }
    categories.clear();
    categories.addAll(validated);
  }

  public void replaceMechanics(Collection<GameMechanic> values) {
    var validated = new LinkedHashSet<>(Objects.requireNonNull(values));
    if (validated.size() > MAX_MECHANICS || validated.contains(null)) {
      throw new IllegalArgumentException("At most " + MAX_MECHANICS + " mechanics are allowed");
    }
    mechanics.clear();
    mechanics.addAll(validated);
  }

  public void replaceDescriptions(Map<String, String> values) {
    var validated = new LinkedHashMap<String, String>();
    Objects.requireNonNull(values)
        .forEach(
            (tag, text) -> {
              String key = localeKey(tag);
              requireText(text, MAX_DESCRIPTION_LENGTH, "description");
              if (validated.putIfAbsent(key, text) != null)
                throw new IllegalArgumentException("Duplicate locale: " + key);
            });
    if (!validated.containsKey(REQUIRED_LOCALE))
      throw new IllegalArgumentException("Description is required for locale " + REQUIRED_LOCALE);
    descriptions.clear();
    descriptions.putAll(validated);
  }

  public record ImageDetails(String url, String altText) {
    public ImageDetails {
      requireText(url, MAX_IMAGE_URL_LENGTH, "image URL");
      if (altText != null
          && altText.codePointCount(0, altText.length()) > MAX_IMAGE_ALT_TEXT_LENGTH) {
        throw new IllegalArgumentException(
            "Image alt text exceeds " + MAX_IMAGE_ALT_TEXT_LENGTH + " characters");
      }
    }
  }

  /** Replaces display slots while retaining existing row IDs and unique sort keys. */
  public void replaceImages(List<ImageDetails> values) {
    var validated = List.copyOf(values);
    int retained = Math.min(images.size(), validated.size());
    for (int index = 0; index < retained; index++) {
      var value = validated.get(index);
      updateImage(images.get(index), value.url(), value.altText());
    }
    while (images.size() > validated.size()) removeImage(images.getLast());
    for (int index = retained; index < validated.size(); index++) {
      var value = validated.get(index);
      addImage(value.url(), value.altText());
    }
  }

  public void putDescription(String locale, String description) {
    String key = localeKey(locale);
    requireText(description, MAX_DESCRIPTION_LENGTH, "description");
    // Only edited historical aliases are canonicalized; untouched persisted content is retained.
    descriptions
        .keySet()
        .removeIf(existing -> existing.equalsIgnoreCase(key) && !existing.equals(key));
    descriptions.put(key, description);
  }

  public void removeDescription(String locale) {
    String key = localeKey(locale);
    if (key.equals(REQUIRED_LOCALE))
      throw new IllegalArgumentException("Description is required for locale " + REQUIRED_LOCALE);
    descriptions.keySet().removeIf(existing -> existing.equalsIgnoreCase(key));
  }

  private static String localeKey(String locale) {
    requireText(locale, MAX_LOCALE_TAG_LENGTH, "locale");
    // Locale.forLanguageTag silently truncates malformed tags; Builder rejects them.
    try {
      String canonical = new Locale.Builder().setLanguageTag(locale).build().toLanguageTag();
      requireText(canonical, MAX_LOCALE_TAG_LENGTH, "locale");
      return canonical;
    } catch (IllformedLocaleException exception) {
      throw new IllegalArgumentException("Invalid BCP 47 locale: " + locale, exception);
    }
  }

  public GameImage addImage(String url, String altText) {
    int next = images.stream().mapToInt(GameImage::getSortOrder).max().orElse(-1);
    var image = new GameImage(this, url, altText, Math.incrementExact(next));
    images.add(image);
    touchImages();
    return image;
  }

  public void updateImage(GameImage image, String url, String altText) {
    requireImage(image);
    image.update(url, altText);
    touchImages();
  }

  public void removeImage(GameImage image) {
    requireImage(image);
    images.remove(image);
    image.detachFromGame();
    touchImages();
  }

  private void requireImage(GameImage image) {
    if (image == null || !images.contains(image)) {
      throw new IllegalArgumentException("Image does not belong to this game");
    }
  }

  private void touchImages() {
    // Inverse collection/child changes alone do not increment the root version.
    updatedAt = Instant.now();
  }

  private static void requireText(String value, int maxLength, String field) {
    if (value == null || value.isBlank() || value.codePointCount(0, value.length()) > maxLength) {
      throw new IllegalArgumentException(
          field + " must be nonblank and at most " + maxLength + " characters");
    }
  }

  private static void requireNonnegative(Integer value, String field) {
    if (value != null && value < 0)
      throw new IllegalArgumentException(field + " must be nonnegative");
  }
}
