package com.chanakanlabs.bgstore.inventory;

import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.name;
import static org.jooq.impl.DSL.noCondition;
import static org.jooq.impl.DSL.table;

import com.chanakanlabs.bgstore.contract.model.GameAvailability;
import com.chanakanlabs.bgstore.contract.model.GameCategory;
import com.chanakanlabs.bgstore.contract.model.GameLifecycle;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.Table;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;

/**
 * jOOQ access to the game catalogue.
 *
 * <p>The project runs jOOQ without generated tables (see {@code HelloController}), so columns are
 * named through {@link DSL#field}. Only {@code game} and {@code game_branch_stock} are touched
 * here; branch names come from the branches module rather than a cross-module join.
 */
@Repository
class GameRepository {

  private static final Table<?> GAME = table(name("game"));
  private static final Field<UUID> ID = field(name("id"), UUID.class);
  private static final Field<String> TITLE = field(name("title"), String.class);
  private static final Field<String> DESCRIPTION = field(name("description"), String.class);
  private static final Field<String> CATEGORY = field(name("category"), String.class);
  private static final Field<Integer> MIN_PLAYERS = field(name("min_players"), Integer.class);
  private static final Field<Integer> MAX_PLAYERS = field(name("max_players"), Integer.class);
  private static final Field<Integer> PLAY_TIME = field(name("play_time_minutes"), Integer.class);
  private static final Field<String> DIFFICULTY = field(name("difficulty"), String.class);
  private static final Field<String[]> TAGS = field(name("tags"), String[].class);
  private static final Field<String> LIFECYCLE = field(name("lifecycle"), String.class);
  private static final Field<OffsetDateTime> CREATED_AT =
      field(name("created_at"), OffsetDateTime.class);
  private static final Field<OffsetDateTime> UPDATED_AT =
      field(name("updated_at"), OffsetDateTime.class);
  private static final Field<OffsetDateTime> LAST_PLAYED_AT =
      field(name("last_played_at"), OffsetDateTime.class);

  private static final Table<?> STOCK = table(name("game_branch_stock"));
  private static final Field<UUID> STOCK_GAME_ID = field(name("game_id"), UUID.class);
  private static final Field<UUID> STOCK_BRANCH_ID = field(name("branch_id"), UUID.class);
  private static final Field<Integer> STOCK_COPIES = field(name("copies"), Integer.class);
  private static final Field<Integer> STOCK_IN_USE = field(name("copies_in_use"), Integer.class);

  /**
   * Rolls stock up per game, derives the display status from it, then pages the result. The window
   * functions run before {@code limit}, so one round trip yields both the page and the totals the
   * stat tiles show for the whole filtered set.
   *
   * <p>The three placeholders take the optional filters; every value is bound, never inlined.
   */
  private static final String LIST_SQL =
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
      select id, title, category, min_players, max_players, copies, available,
             branch_count, single_branch_id, status,
             count(*) over () as total_elements,
             sum(available) over () as total_available,
             sum(in_use) over () as total_in_use
      from rolled
      ${statusFilter}
      order by title, id
      limit ? offset ?
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

    binds.add(filter.size());
    binds.add(filter.page() * filter.size());

    var rows =
        database.fetch(
            LIST_SQL
                .replace("${stockFilter}", stockWhere)
                .replace("${gameFilter}", gameWhere)
                .replace("${statusFilter}", statusWhere),
            binds.toArray());
    if (rows.isEmpty()) {
      return GamePage.EMPTY;
    }

    var first = rows.getFirst();

    return new GamePage(
        rows.map(GameRepository::toSummaryRow),
        first.get("total_elements", Long.class),
        first.get("total_available", Long.class),
        first.get("total_in_use", Long.class));
  }

  Optional<StoredGame> findById(UUID id) {
    return database
        .select(
            ID,
            TITLE,
            DESCRIPTION,
            CATEGORY,
            MIN_PLAYERS,
            MAX_PLAYERS,
            PLAY_TIME,
            DIFFICULTY,
            TAGS,
            LIFECYCLE,
            CREATED_AT,
            LAST_PLAYED_AT)
        .from(GAME)
        .where(ID.eq(id))
        .fetchOptional(GameRepository::toStoredGame);
  }

  List<BranchStockRow> findStock(UUID gameId) {
    return database
        .select(STOCK_BRANCH_ID, STOCK_COPIES, STOCK_IN_USE)
        .from(STOCK)
        .where(STOCK_GAME_ID.eq(gameId))
        .fetch(
            row ->
                new BranchStockRow(
                    row.get(STOCK_BRANCH_ID), row.get(STOCK_COPIES), row.get(STOCK_IN_USE)));
  }

  UUID insert(GameCommand command) {
    var id = UUID.randomUUID();
    database
        .insertInto(GAME)
        .set(ID, id)
        .set(TITLE, command.title())
        .set(DESCRIPTION, command.description())
        .set(CATEGORY, command.category().getValue())
        .set(MIN_PLAYERS, command.minPlayers())
        .set(MAX_PLAYERS, command.maxPlayers())
        .set(PLAY_TIME, command.playTimeMinutes())
        .set(DIFFICULTY, command.difficulty())
        .set(TAGS, command.tags().toArray(String[]::new))
        .set(LIFECYCLE, command.lifecycle().getValue())
        .execute();

    return id;
  }

  /** Returns whether a game with this id existed. */
  boolean update(UUID id, GameCommand command) {
    return database
            .update(GAME)
            .set(TITLE, command.title())
            .set(DESCRIPTION, command.description())
            .set(CATEGORY, command.category().getValue())
            .set(MIN_PLAYERS, command.minPlayers())
            .set(MAX_PLAYERS, command.maxPlayers())
            .set(PLAY_TIME, command.playTimeMinutes())
            .set(DIFFICULTY, command.difficulty())
            .set(TAGS, command.tags().toArray(String[]::new))
            .set(LIFECYCLE, command.lifecycle().getValue())
            .set(UPDATED_AT, DSL.currentOffsetDateTime())
            .where(ID.eq(id))
            .execute()
        > 0;
  }

  /** Returns whether a game with this id existed. */
  boolean retire(UUID id) {
    return database
            .update(GAME)
            .set(LIFECYCLE, GameLifecycle.RETIRED.getValue())
            .set(UPDATED_AT, DSL.currentOffsetDateTime())
            .where(ID.eq(id))
            .execute()
        > 0;
  }

  /**
   * Brings the stored stock in line with {@code copies}. Rows are upserted rather than rewritten so
   * the copies a play session is holding survive an edit of the catalogue entry.
   */
  void replaceStock(UUID gameId, Map<UUID, Integer> copies) {
    database
        .deleteFrom(STOCK)
        .where(
            STOCK_GAME_ID
                .eq(gameId)
                .and(copies.isEmpty() ? noCondition() : STOCK_BRANCH_ID.notIn(copies.keySet())))
        .execute();

    copies.forEach(
        (branchId, count) ->
            database
                .insertInto(STOCK, STOCK_GAME_ID, STOCK_BRANCH_ID, STOCK_COPIES)
                .values(gameId, branchId, count)
                .onConflict(STOCK_GAME_ID, STOCK_BRANCH_ID)
                .doUpdate()
                .set(STOCK_COPIES, count)
                .execute());
  }

  private static GameSummaryRow toSummaryRow(Record row) {
    var lifecycle = row.get("status", String.class);

    return new GameSummaryRow(
        row.get("id", UUID.class),
        row.get("title", String.class),
        GameCategory.fromValue(row.get("category", String.class)),
        row.get("min_players", Integer.class),
        row.get("max_players", Integer.class),
        row.get("copies", Integer.class),
        row.get("available", Integer.class),
        row.get("branch_count", Integer.class),
        row.get("single_branch_id", UUID.class),
        GameAvailability.fromValue(lifecycle));
  }

  private static StoredGame toStoredGame(Record row) {
    return new StoredGame(
        row.get(ID),
        row.get(TITLE),
        row.get(DESCRIPTION),
        GameCategory.fromValue(row.get(CATEGORY)),
        row.get(MIN_PLAYERS),
        row.get(MAX_PLAYERS),
        row.get(PLAY_TIME),
        row.get(DIFFICULTY),
        List.of(Objects.requireNonNullElse(row.get(TAGS), new String[0])),
        GameLifecycle.fromValue(row.get(LIFECYCLE)),
        row.get(CREATED_AT),
        row.get(LAST_PLAYED_AT));
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
