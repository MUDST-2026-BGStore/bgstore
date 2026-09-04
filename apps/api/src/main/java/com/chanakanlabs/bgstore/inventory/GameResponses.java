package com.chanakanlabs.bgstore.inventory;

import com.chanakanlabs.bgstore.branches.Branch;
import com.chanakanlabs.bgstore.contract.model.BranchStock;
import com.chanakanlabs.bgstore.contract.model.GameDetail;
import com.chanakanlabs.bgstore.contract.model.GameListResponse;
import com.chanakanlabs.bgstore.contract.model.GameStats;
import com.chanakanlabs.bgstore.contract.model.GameSummary;
import com.chanakanlabs.bgstore.contract.model.LocalizedDescription;
import com.chanakanlabs.bgstore.contract.model.LocalizedTitle;
import com.chanakanlabs.bgstore.contract.model.PageMeta;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.lang.Nullable;

/** Turns the stored shapes into the contract models. Pure: no database, no branch lookups. */
final class GameResponses {

  private GameResponses() {}

  static GameListResponse toListResponse(
      GamePage page, GameFilter filter, Map<UUID, String> branchNames) {
    var items = page.rows().stream().map(row -> toSummary(row, branchNames)).toList();

    var totalPages = (int) ((page.totalElements() + filter.size() - 1) / filter.size());
    var pageMeta = new PageMeta(filter.page(), filter.size(), page.totalElements(), totalPages);
    var stats = new GameStats(page.totalElements(), page.totalAvailable(), page.totalInUse());

    return new GameListResponse(items, pageMeta, stats);
  }

  static GameSummary toSummary(GameSummaryRow row, Map<UUID, String> branchNames) {
    var summary =
        new GameSummary(
            row.id(),
            titleOf(row.title()),
            row.category(),
            row.minPlayers(),
            row.maxPlayers(),
            row.branchCount(),
            row.copies(),
            row.available(),
            row.status());

    // The list shows one row per game, so a branch name is only meaningful when
    // the figures come from exactly one branch.
    var singleBranchId = row.singleBranchId();
    if (row.branchCount() == 1 && singleBranchId != null) {
      summary.setBranchName(branchNames.get(singleBranchId));
    }

    return summary;
  }

  /**
   * Builds the detail view. Every branch in the directory gets a row, including branches holding no
   * copies, because the copies-by-branch table shows those as "not stocked".
   */
  static GameDetail toDetail(StoredGame game, List<BranchStockRow> stock, List<Branch> branches) {
    var byBranch =
        stock.stream().collect(Collectors.toMap(BranchStockRow::branchId, Function.identity()));

    var rows =
        branches.stream()
            .map(
                branch -> {
                  var held = byBranch.get(branch.id());
                  var copies = held == null ? 0 : held.copies();
                  var inUse = held == null ? 0 : held.inUse();

                  return new BranchStock(
                      branch.id(),
                      branch.name(),
                      copies,
                      copies - inUse,
                      inUse,
                      GameAvailabilities.of(game.lifecycle(), copies, copies - inUse));
                })
            .toList();

    var totalCopies = rows.stream().mapToInt(BranchStock::getCopies).sum();
    var totalAvailable = rows.stream().mapToInt(BranchStock::getAvailable).sum();
    var branchCount = (int) rows.stream().filter(row -> row.getCopies() > 0).count();

    var detail =
        new GameDetail(
            game.id(),
            titleOf(game.title()),
            game.category(),
            game.minPlayers(),
            game.maxPlayers(),
            game.tags(),
            game.lifecycle(),
            GameAvailabilities.of(game.lifecycle(), totalCopies, totalAvailable),
            game.addedAt(),
            totalCopies,
            branchCount,
            rows);
    detail.setDescription(descriptionOf(game.description()));
    detail.setPlayTimeMinutes(game.playTimeMinutes());
    detail.setDifficulty(game.difficulty());
    detail.setLastPlayedAt(game.lastPlayedAt());

    return detail;
  }

  /**
   * Both languages go out on every response: the browser renders one through the fallback rule the
   * contract publishes, and the edit form fills in both.
   */
  private static LocalizedTitle titleOf(LocalizedText title) {
    // Stored titles always carry English: the column is not null and the
    // validator requires it before a row is written.
    var model = new LocalizedTitle(Objects.requireNonNull(title.english()));
    model.setTh(title.thai());

    return model;
  }

  /** Null when neither language carries text, which is how "no description" is published. */
  private static @Nullable LocalizedDescription descriptionOf(LocalizedText description) {
    if (description.isEmpty()) {
      return null;
    }

    var model = new LocalizedDescription();
    model.setEn(description.english());
    model.setTh(description.thai());

    return model;
  }
}
