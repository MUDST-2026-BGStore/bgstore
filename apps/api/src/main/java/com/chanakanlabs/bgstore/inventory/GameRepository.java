package com.chanakanlabs.bgstore.inventory;

import static com.chanakanlabs.bgstore.database.Tables.GAME;
import static com.chanakanlabs.bgstore.database.Tables.GAME_BRANCH_STOCK;
import static org.jooq.impl.DSL.noCondition;

import com.chanakanlabs.bgstore.contract.model.GameCategory;
import com.chanakanlabs.bgstore.contract.model.GameLifecycle;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;

/**
 * jOOQ access to the game catalogue, through the table types the {@code jooqCodegen} task generates
 * from the Flyway migrations.
 *
 * <p>Only {@code game} and {@code game_branch_stock} are touched here; branch names come from the
 * branches module rather than a cross-module join.
 */
@Repository
class GameRepository {

  /**
   * Rolls stock up per game over the branches the filter selects, and derives the status the {@code
   * status} filter matches on.
   *
   * <p>The {@code status} case has to live in SQL so the filter can be applied and paged in the
   * database. What a row actually reports is still derived once, in {@link GameAvailabilities},
   * from the lifecycle and counts selected here — the case below only decides which rows come back.
   *
   * <p>The placeholders take the optional filters; every value is bound, never inlined.
   */
  private static final String ROLLED_STOCK_SQL =
      """
      with scoped_stock as (
          select s.game_id,
                 cast(sum(s.copies) as int) as copies,
                 cast(sum(s.copies - s.copies_in_use) as int) as available,
                 cast(sum(s.copies_in_use) as int) as in_use,
                 cast(count(*) filter (where s.copies > 0) as int) as branch_count,
                 (array_agg(s.branch_id) filter (where s.copies > 0))[1] as single_branch_id
          from game_branch_stock s
          ${stockFilter}
          group by s.game_id
      ),
      rolled as (
          select g.id,
                 g.title,
                 g.category,
                 g.min_players,
                 g.max_players,
                 g.lifecycle,
                 coalesce(st.copies, 0) as copies,
                 coalesce(st.available, 0) as available,
                 coalesce(st.in_use, 0) as in_use,
                 coalesce(st.branch_count, 0) as branch_count,
                 st.single_branch_id,
                 case
                     when g.lifecycle = 'retired' then 'retired'
                     when coalesce(st.copies, 0) = 0 then 'notStocked'
                     when coalesce(st.available, 0) = 0 then 'allCopiesOut'
                     else 'available'
                 end as status
          from game g
          left join scoped_stock st on st.game_id = g.id
          ${gameFilter}
      )
      """;

  private static final String PAGE_SQL =
      ROLLED_STOCK_SQL
          + """
          select id, title, category, min_players, max_players, lifecycle,
                 copies, available, branch_count, single_branch_id
          from rolled
          ${statusFilter}
          order by title, id
          limit ? offset ?
          """;

  /**
   * The stat tiles and the "showing x of y" line describe the whole filtered set, so they are
   * counted separately. Reading them off the page would report zero for any page past the last one.
   */
  private static final String TOTALS_SQL =
      ROLLED_STOCK_SQL
          + """
          select count(*) as total_elements,
                 coalesce(sum(available), 0) as total_available,
                 coalesce(sum(in_use), 0) as total_in_use
          from rolled
          ${statusFilter}
          """;

  private final DSLContext database;

  GameRepository(DSLContext database) {
    this.database = database;
  }

  GamePage findPage(GameFilter filter) {
    var binds = new ArrayList<Object>();

    var stockWhere = "";
    if (filter.branchId() != null) {
      stockWhere = "where s.branch_id = ?";
      binds.add(filter.branchId());
    }

    var gameConditions = new ArrayList<String>();
    if (filter.category() != null) {
      gameConditions.add("g.category = ?");
      binds.add(filter.category().getValue());
    }
    var search = filter.search();
    if (search != null && !search.isBlank()) {
      gameConditions.add("lower(g.title) like ?");
      binds.add(likePattern(search));
    }
    var gameWhere = gameConditions.isEmpty() ? "" : "where " + String.join(" and ", gameConditions);

    var statusWhere = "";
    if (filter.status() != null) {
      statusWhere = "where status = ?";
      binds.add(filter.status().getValue());
    }

    var totals =
        database.fetchSingle(
            applyFilters(TOTALS_SQL, stockWhere, gameWhere, statusWhere), binds.toArray());

    var pageBinds = new ArrayList<>(binds);
    pageBinds.add(filter.size());
    pageBinds.add(filter.page() * filter.size());
    var rows =
        database.fetch(
            applyFilters(PAGE_SQL, stockWhere, gameWhere, statusWhere), pageBinds.toArray());

    return new GamePage(
        rows.map(GameRepository::toSummaryRow),
        totals.get("total_elements", Long.class),
        totals.get("total_available", Long.class),
        totals.get("total_in_use", Long.class));
  }

  private static String applyFilters(
      String sql, String stockWhere, String gameWhere, String statusWhere) {
    return sql.replace("${stockFilter}", stockWhere)
        .replace("${gameFilter}", gameWhere)
        .replace("${statusFilter}", statusWhere);
  }

  Optional<StoredGame> findById(UUID id) {
    return database
        .select(
            GAME.ID,
            GAME.TITLE,
            GAME.DESCRIPTION,
            GAME.CATEGORY,
            GAME.MIN_PLAYERS,
            GAME.MAX_PLAYERS,
            GAME.PLAY_TIME_MINUTES,
            GAME.DIFFICULTY,
            GAME.TAGS,
            GAME.LIFECYCLE,
            GAME.CREATED_AT,
            GAME.LAST_PLAYED_AT)
        .from(GAME)
        .where(GAME.ID.eq(id))
        .fetchOptional(GameRepository::toStoredGame);
  }

  List<BranchStockRow> findStock(UUID gameId) {
    return database
        .select(
            GAME_BRANCH_STOCK.BRANCH_ID, GAME_BRANCH_STOCK.COPIES, GAME_BRANCH_STOCK.COPIES_IN_USE)
        .from(GAME_BRANCH_STOCK)
        .where(GAME_BRANCH_STOCK.GAME_ID.eq(gameId))
        .fetch(
            row ->
                new BranchStockRow(
                    row.get(GAME_BRANCH_STOCK.BRANCH_ID),
                    row.get(GAME_BRANCH_STOCK.COPIES),
                    row.get(GAME_BRANCH_STOCK.COPIES_IN_USE)));
  }

  UUID insert(GameCommand command) {
    var id = UUID.randomUUID();
    var columns = new LinkedHashMap<Field<?>, Object>(columnsOf(command));
    columns.put(GAME.ID, id);
    database.insertInto(GAME).set(columns).execute();

    return id;
  }

  /** Returns whether a game with this id existed. */
  boolean update(UUID id, GameCommand command) {
    var columns = new LinkedHashMap<Field<?>, Object>(columnsOf(command));
    columns.put(GAME.UPDATED_AT, DSL.currentOffsetDateTime());

    return database.update(GAME).set(columns).where(GAME.ID.eq(id)).execute() > 0;
  }

  /** The columns a create and a replace both write, so the two cannot drift apart. */
  private static Map<Field<?>, Object> columnsOf(GameCommand command) {
    var columns = new LinkedHashMap<Field<?>, Object>();
    columns.put(GAME.TITLE, command.title());
    columns.put(GAME.DESCRIPTION, command.description());
    columns.put(GAME.CATEGORY, command.category().getValue());
    columns.put(GAME.MIN_PLAYERS, command.minPlayers());
    columns.put(GAME.MAX_PLAYERS, command.maxPlayers());
    columns.put(GAME.PLAY_TIME_MINUTES, command.playTimeMinutes());
    columns.put(GAME.DIFFICULTY, command.difficulty());
    columns.put(GAME.TAGS, command.tags().toArray(String[]::new));
    columns.put(GAME.LIFECYCLE, command.lifecycle().getValue());

    return columns;
  }

  /** Returns whether a game with this id existed. */
  boolean retire(UUID id) {
    return database
            .update(GAME)
            .set(GAME.LIFECYCLE, GameLifecycle.RETIRED.getValue())
            .set(GAME.UPDATED_AT, DSL.currentOffsetDateTime())
            .where(GAME.ID.eq(id))
            .execute()
        > 0;
  }

  /**
   * Brings the stored stock in line with {@code copies}. Rows are upserted rather than rewritten so
   * the copies a play session is holding survive an edit of the catalogue entry.
   */
  void replaceStock(UUID gameId, Map<UUID, Integer> copies) {
    database
        .deleteFrom(GAME_BRANCH_STOCK)
        .where(
            GAME_BRANCH_STOCK
                .GAME_ID
                .eq(gameId)
                .and(
                    copies.isEmpty()
                        ? noCondition()
                        : GAME_BRANCH_STOCK.BRANCH_ID.notIn(copies.keySet())))
        .execute();

    copies.forEach(
        (branchId, count) ->
            database
                .insertInto(
                    GAME_BRANCH_STOCK,
                    GAME_BRANCH_STOCK.GAME_ID,
                    GAME_BRANCH_STOCK.BRANCH_ID,
                    GAME_BRANCH_STOCK.COPIES)
                .values(gameId, branchId, count)
                .onConflict(GAME_BRANCH_STOCK.GAME_ID, GAME_BRANCH_STOCK.BRANCH_ID)
                .doUpdate()
                .set(GAME_BRANCH_STOCK.COPIES, count)
                .execute());
  }

  private static GameSummaryRow toSummaryRow(Record row) {
    var lifecycle = GameLifecycle.fromValue(row.get("lifecycle", String.class));
    int copies = row.get("copies", Integer.class);
    int available = row.get("available", Integer.class);

    return new GameSummaryRow(
        row.get("id", UUID.class),
        row.get("title", String.class),
        GameCategory.fromValue(row.get("category", String.class)),
        row.get("min_players", Integer.class),
        row.get("max_players", Integer.class),
        copies,
        available,
        row.get("branch_count", Integer.class),
        row.get("single_branch_id", UUID.class),
        // The list and the detail report the same status for the same numbers.
        GameAvailabilities.of(lifecycle, copies, available));
  }

  private static StoredGame toStoredGame(Record row) {
    return new StoredGame(
        row.get(GAME.ID),
        row.get(GAME.TITLE),
        row.get(GAME.DESCRIPTION),
        GameCategory.fromValue(row.get(GAME.CATEGORY)),
        row.get(GAME.MIN_PLAYERS),
        row.get(GAME.MAX_PLAYERS),
        row.get(GAME.PLAY_TIME_MINUTES),
        row.get(GAME.DIFFICULTY),
        List.of(Objects.requireNonNullElse(row.get(GAME.TAGS), new String[0])),
        GameLifecycle.fromValue(row.get(GAME.LIFECYCLE)),
        row.get(GAME.CREATED_AT),
        row.get(GAME.LAST_PLAYED_AT));
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
