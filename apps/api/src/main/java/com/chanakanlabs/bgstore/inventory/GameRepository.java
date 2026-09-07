package com.chanakanlabs.bgstore.inventory;

import com.chanakanlabs.bgstore.contract.model.CatalogueLocale;
import com.chanakanlabs.bgstore.contract.model.GameCategory;
import com.chanakanlabs.bgstore.contract.model.GameLifecycle;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Access to the game catalogue over Spring Data JPA.
 *
 * <p>Only {@code game} and {@code game_branch_stock} are touched here; branch names come from the
 * branches module rather than a cross-module join.
 */
@Repository
@Transactional
class GameRepository {

  private final GameJpaRepository games;
  private final GameBranchStockJpaRepository stock;

  GameRepository(GameJpaRepository games, GameBranchStockJpaRepository stock) {
    this.games = games;
    this.stock = stock;
  }

  @Transactional(readOnly = true)
  GamePage findPage(GameFilter filter) {
    var category = filter.category() == null ? null : filter.category().getValue();
    var status = filter.status() == null ? null : filter.status().getValue();
    var search = filter.search();
    var pattern = search == null || search.isBlank() ? null : likePattern(search);
    var locale = filter.locale() == CatalogueLocale.TH ? "th" : "en";

    var rows =
        games.findPage(
            filter.branchId(),
            category,
            pattern,
            status,
            locale,
            filter.size(),
            filter.page() * filter.size());
    var totals = games.findTotals(filter.branchId(), category, pattern, status).get(0);

    return new GamePage(
        rows.stream().map(GameRepository::toSummaryRow).toList(),
        ((Number) totals[0]).longValue(),
        ((Number) totals[1]).longValue(),
        ((Number) totals[2]).longValue());
  }

  @Transactional(readOnly = true)
  Optional<StoredGame> findById(UUID id) {
    return games.findById(id).map(GameEntity::toStoredGame);
  }

  @Transactional(readOnly = true)
  List<BranchStockRow> findStock(UUID gameId) {
    return stock.findByIdGameId(gameId).stream().map(GameBranchStockEntity::toRow).toList();
  }

  UUID insert(GameCommand command) {
    var game = new GameEntity(UUID.randomUUID());
    game.apply(command);

    return games.save(game).toStoredGame().id();
  }

  /** Returns whether a game with this id existed. */
  boolean update(UUID id, GameCommand command) {
    return games
        .findById(id)
        .map(
            game -> {
              game.apply(command);
              games.save(game);
              return true;
            })
        .orElse(false);
  }

  /** Returns whether a game with this id existed. */
  boolean retire(UUID id) {
    return games
        .findById(id)
        .map(
            game -> {
              game.retire();
              games.save(game);
              return true;
            })
        .orElse(false);
  }

  /**
   * Brings the stored stock in line with {@code copies}. Rows are upserted rather than rewritten so
   * the copies a play session is holding survive an edit of the catalogue entry.
   */
  void replaceStock(UUID gameId, Map<UUID, Integer> copies) {
    var existing = stock.findByIdGameId(gameId);
    stock.deleteAll(
        existing.stream().filter(row -> !copies.containsKey(row.toRow().branchId())).toList());

    var byBranch =
        existing.stream()
            .collect(
                java.util.stream.Collectors.toMap(
                    row -> row.toRow().branchId(), row -> row, (a, b) -> a));
    copies.forEach(
        (branchId, count) -> {
          var row = byBranch.get(branchId);
          if (row == null) {
            stock.save(new GameBranchStockEntity(new GameBranchStockId(gameId, branchId), count));
          } else {
            row.setCopies(count);
            stock.save(row);
          }
        });
  }

  private static GameSummaryRow toSummaryRow(Object[] row) {
    var lifecycle = GameLifecycle.fromValue((String) row[6]);
    int copies = ((Number) row[7]).intValue();
    int available = ((Number) row[8]).intValue();

    return new GameSummaryRow(
        (UUID) row[0],
        new LocalizedText((String) row[1], (String) row[2]),
        GameCategory.fromValue((String) row[3]),
        ((Number) row[4]).intValue(),
        ((Number) row[5]).intValue(),
        copies,
        available,
        ((Number) row[9]).intValue(),
        (UUID) row[10],
        // The list and the detail report the same status for the same numbers.
        GameAvailabilities.of(lifecycle, copies, available));
  }

  /** Wildcards typed into the search box match themselves rather than acting as wildcards. */
  private static String likePattern(String search) {
    var escaped =
        search
            .strip()
            .toLowerCase(Locale.ROOT)
            .replace("\\", "\\\\")
            .replace("%", "\\%")
            .replace("_", "\\_");

    return "%" + escaped + "%";
  }
}
