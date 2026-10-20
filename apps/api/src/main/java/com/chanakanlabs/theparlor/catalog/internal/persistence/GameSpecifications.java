package com.chanakanlabs.theparlor.catalog.internal.persistence;

import com.chanakanlabs.theparlor.catalog.internal.application.GameSearchCriteria;
import com.chanakanlabs.theparlor.catalog.internal.domain.Game;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;

/**
 * Translates search criteria into database predicates without multiplying paginated result rows.
 */
public final class GameSpecifications {
  private GameSpecifications() {}

  public static Specification<Game> matching(GameSearchCriteria filters, UUID after) {
    return (root, query, cb) -> {
      var predicates = new ArrayList<Predicate>();
      predicates.add(cb.isTrue(root.get("active")));
      if (after != null) predicates.add(cb.greaterThan(root.get("id"), after));
      if (filters.q() != null) {
        String pattern =
            "%" + filters.q().replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
        predicates.add(
            cb.or(
                cb.like(cb.lower(root.get("name")), pattern, '!'),
                cb.like(cb.lower(root.get("designer")), pattern, '!')));
      }
      if (filters.difficulty() != null)
        predicates.add(cb.equal(root.get("difficulty"), filters.difficulty()));
      if (filters.players() != null) {
        predicates.add(cb.lessThanOrEqualTo(root.get("minPlayers"), filters.players()));
        predicates.add(cb.greaterThanOrEqualTo(root.get("maxPlayers"), filters.players()));
      }
      if (!filters.categories().isEmpty()) {
        var count = query.subquery(Long.class);
        var link = count.correlate(root).join("categories");
        count
            .select(cb.countDistinct(link.get("name")))
            .where(link.get("name").in(filters.categories()));
        predicates.add(cb.equal(count, (long) filters.categories().size()));
      }
      if (!filters.mechanics().isEmpty()) {
        var count = query.subquery(Long.class);
        var link = count.correlate(root).join("mechanics");
        count
            .select(cb.countDistinct(link.get("name")))
            .where(link.get("name").in(filters.mechanics()));
        predicates.add(cb.equal(count, (long) filters.mechanics().size()));
      }
      return cb.and(predicates.toArray(Predicate[]::new));
    };
  }
}
