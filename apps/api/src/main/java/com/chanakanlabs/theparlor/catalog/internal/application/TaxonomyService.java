package com.chanakanlabs.theparlor.catalog.internal.application;

import com.chanakanlabs.theparlor.catalog.internal.domain.GameCategory;
import com.chanakanlabs.theparlor.catalog.internal.domain.GameMechanic;
import com.chanakanlabs.theparlor.catalog.internal.mapper.GameMapper;
import com.chanakanlabs.theparlor.catalog.internal.persistence.GameCategoryRepository;
import com.chanakanlabs.theparlor.catalog.internal.persistence.GameMechanicRepository;
import com.chanakanlabs.theparlor.contracts.model.*;
import java.util.Map;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional(readOnly = true)
public class TaxonomyService {
  private final GameCategoryRepository categories;
  private final GameMechanicRepository mechanics;
  private final GameMapper mapper;

  public TaxonomyService(
      GameCategoryRepository categories, GameMechanicRepository mechanics, GameMapper mapper) {
    this.categories = categories;
    this.mechanics = mechanics;
    this.mapper = mapper;
  }

  public Category category(long id) {
    return mapper.toCategory(categories.findById(id).orElseThrow(TaxonomyService::missing));
  }

  public Mechanic mechanic(long id) {
    return mapper.toMechanic(mechanics.findById(id).orElseThrow(TaxonomyService::missing));
  }

  public CategoryPage categories(Integer requestedLimit, String cursor) {
    int limit = CatalogCursor.limit(requestedLimit);
    String key = CatalogCursor.after(cursor, "categories", "");
    Long after = key == null ? null : positiveId(key);
    Specification<GameCategory> spec =
        (root, query, cb) ->
            after == null ? cb.conjunction() : cb.greaterThan(root.get("id"), after);
    var rows = categories.findBy(spec, query -> query.sortBy(Sort.by("id")).limit(limit + 1).all());
    var items = rows.stream().limit(limit).map(mapper::toCategory).toList();
    var result = new CategoryPage(items);
    if (rows.size() > limit)
      result.setNextCursor(CatalogCursor.next("categories", "", items.getLast().getId()));
    return result;
  }

  public MechanicPage mechanics(Integer requestedLimit, String cursor) {
    int limit = CatalogCursor.limit(requestedLimit);
    String key = CatalogCursor.after(cursor, "mechanics", "");
    Long after = key == null ? null : positiveId(key);
    Specification<GameMechanic> spec =
        (root, query, cb) ->
            after == null ? cb.conjunction() : cb.greaterThan(root.get("id"), after);
    var rows = mechanics.findBy(spec, query -> query.sortBy(Sort.by("id")).limit(limit + 1).all());
    var items = rows.stream().limit(limit).map(mapper::toMechanic).toList();
    var result = new MechanicPage(items);
    if (rows.size() > limit)
      result.setNextCursor(CatalogCursor.next("mechanics", "", items.getLast().getId()));
    return result;
  }

  @Transactional
  @PreAuthorize("hasAnyRole('MANAGER', 'ADMIN')")
  public Category createCategory(String name, Map<String, String> names) {
    var entity = GameCategory.create(name);
    if (names != null) entity.replaceNames(names);
    return mapper.toCategory(categories.saveAndFlush(entity));
  }

  @Transactional
  @PreAuthorize("hasAnyRole('MANAGER', 'ADMIN')")
  public Mechanic createMechanic(String name, Map<String, String> names) {
    var entity = GameMechanic.create(name);
    if (names != null) entity.replaceNames(names);
    return mapper.toMechanic(mechanics.saveAndFlush(entity));
  }

  @Transactional
  @PreAuthorize("hasAnyRole('MANAGER', 'ADMIN')")
  public Category updateCategory(long id, Map<String, String> names) {
    var entity = categories.findById(id).orElseThrow(TaxonomyService::missing);
    entity.replaceNames(names);
    categories.flush();
    return mapper.toCategory(entity);
  }

  @Transactional
  @PreAuthorize("hasAnyRole('MANAGER', 'ADMIN')")
  public Mechanic updateMechanic(long id, Map<String, String> names) {
    var entity = mechanics.findById(id).orElseThrow(TaxonomyService::missing);
    entity.replaceNames(names);
    mechanics.flush();
    return mapper.toMechanic(entity);
  }

  private static long positiveId(String key) {
    long id = Long.parseLong(key);
    if (id < 1) throw new IllegalArgumentException("Invalid cursor ID");
    return id;
  }

  private static ResponseStatusException missing() {
    return new ResponseStatusException(HttpStatus.NOT_FOUND, "Taxonomy entry not found");
  }
}
