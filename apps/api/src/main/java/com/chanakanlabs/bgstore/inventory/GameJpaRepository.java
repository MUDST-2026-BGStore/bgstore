package com.chanakanlabs.bgstore.inventory;

import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.NativeQuery;
import org.springframework.data.repository.query.Param;

/**
 * CRUD over {@link GameEntity} plus the two rolled-up reads the inventory list needs.
 *
 * <p>The rollup stays native SQL: it aggregates stock per game and derives the availability status
 * so the {@code status} filter can be applied and paged in the database. JPQL has no {@code
 * filter}, {@code array_agg} or common table expressions, so a derived query cannot express it.
 *
 * <p>Every filter is optional and guarded with {@code is null} rather than concatenated in, so the
 * query is one static string and every value stays a bind parameter. PostgreSQL cannot infer the
 * type of a null bind, hence the casts.
 */
interface GameJpaRepository extends JpaRepository<GameEntity, UUID> {

  String ROLLED =
      """
      with scoped_stock as (
          select s.game_id,
                 cast(sum(s.copies) as int) as copies,
                 cast(sum(s.copies - s.copies_in_use) as int) as available,
                 cast(sum(s.copies_in_use) as int) as in_use,
                 cast(count(*) filter (where s.copies > 0) as int) as branch_count,
                 (array_agg(s.branch_id) filter (where s.copies > 0))[1] as single_branch_id
          from game_branch_stock s
          where (cast(:branchId as uuid) is null or s.branch_id = cast(:branchId as uuid))
          group by s.game_id
      ),
      rolled as (
          select g.id,
                 g.title_en,
                 g.title_th,
                 g.category,
                 g.min_players,
                 g.max_players,
                 g.lifecycle,
                 g.play_time_minutes,
                 g.image_urls[1] as cover_image_url,
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
          where (cast(:category as text) is null or g.category = cast(:category as text))
            and (cast(:lifecycle as text) is null or g.lifecycle = cast(:lifecycle as text))
            and (cast(:search as text) is null
                 or lower(g.title_en) like cast(:search as text)
                 or lower(g.title_th) like cast(:search as text))
      )
      """;

  /**
   * The requested locale decides the ordering, so a paged list reads in the order the reader sees.
   * The English title is never null, so this always orders on a value.
   */
  @NativeQuery(
      value =
          ROLLED
              + """
              select id, title_en, title_th, category, min_players, max_players, lifecycle,
                     copies, available, branch_count, single_branch_id,
                     play_time_minutes, cover_image_url
              from rolled
              where (cast(:status as text) is null or status = cast(:status as text))
              order by case when cast(:locale as text) = 'th' then coalesce(title_th, title_en)
                            else title_en end, id
              limit :size offset :offset
              """)
  List<Object[]> findPage(
      @Param("branchId") @Nullable UUID branchId,
      @Param("category") @Nullable String category,
      @Param("lifecycle") @Nullable String lifecycle,
      @Param("search") @Nullable String search,
      @Param("status") @Nullable String status,
      @Param("locale") String locale,
      @Param("size") int size,
      @Param("offset") int offset);

  /**
   * The stat tiles and the "showing x of y" line describe the whole filtered set, so they are
   * counted separately. Reading them off the page would report zero for any page past the last one.
   */
  @NativeQuery(
      value =
          ROLLED
              + """
              select count(*), coalesce(sum(available), 0), coalesce(sum(in_use), 0)
              from rolled
              where (cast(:status as text) is null or status = cast(:status as text))
              """)
  List<Object[]> findTotals(
      @Param("branchId") @Nullable UUID branchId,
      @Param("category") @Nullable String category,
      @Param("lifecycle") @Nullable String lifecycle,
      @Param("search") @Nullable String search,
      @Param("status") @Nullable String status);
}
