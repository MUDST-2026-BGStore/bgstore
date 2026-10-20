package com.chanakanlabs.theparlor.catalog.internal.domain;

import static com.chanakanlabs.theparlor.catalog.internal.CatalogLimits.*;

import jakarta.persistence.*;
import java.util.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.DynamicUpdate;

@Entity
@DynamicUpdate
@Table(name = "game_mechanics")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GameMechanic {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Version private Long version;

  @ElementCollection
  @CollectionTable(name = "game_mechanic_names", joinColumns = @JoinColumn(name = "mechanic_id"))
  @MapKeyColumn(name = "locale", length = MAX_LOCALE_TAG_LENGTH, nullable = false)
  @Column(name = "name", length = MAX_LOCALIZED_NAME_LENGTH, nullable = false)
  @BatchSize(size = COLLECTION_BATCH_SIZE)
  @Getter(AccessLevel.NONE)
  private Map<String, String> names = new LinkedHashMap<>();

  public Map<String, String> getNames() {
    return Collections.unmodifiableMap(names);
  }

  public void replaceNames(Map<String, String> names) {
    var validated = LocalizedNames.validate(names);
    this.names.clear();
    this.names.putAll(validated);
  }

  @Column(nullable = false, length = MAX_TAXONOMY_KEY_LENGTH, unique = true)
  private String name;

  public static GameMechanic create(String name) {
    TaxonomyKey.validate(name);
    var value = new GameMechanic();
    value.name = name;
    value.replaceNames(Map.of(REQUIRED_LOCALE, name));
    return value;
  }

  @Override
  public boolean equals(Object other) {
    return this == other
        || (other instanceof GameMechanic value
            && getId() != null
            && getId().equals(value.getId()));
  }

  @Override
  public int hashCode() {
    // Stable before/after generated ID assignment, including Hibernate proxies.
    return GameMechanic.class.hashCode();
  }
}
