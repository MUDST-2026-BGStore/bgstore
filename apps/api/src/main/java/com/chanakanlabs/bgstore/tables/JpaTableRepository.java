package com.chanakanlabs.bgstore.tables;

import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.NativeQuery;
import org.springframework.data.repository.query.Param;

/** Database adapter for the table-management repository port. */
interface JpaTableRepository extends JpaRepository<TableEntity, Long> {

  @NativeQuery(
      value =
          """
      select t.* from store_table t join branch b on b.id = t.branch_id
      where (:branch is null or :branch = '' or lower(b.name) = lower(:branch))
        and (:zone is null or :zone = '' or lower(:zone) = 'all zones' or lower(t.zone) = lower(:zone))
        and (:status is null or :status = '' or lower(:status) = 'all statuses' or lower(t.status) = lower(:status))
        and (:search is null or :search = '' or lower(t.name) like lower(concat('%', :search, '%'))
             or lower(t.zone) like lower(concat('%', :search, '%'))
             or cast(t.id as text) = :search)
        and (:activeOnly = false or t.active = true)
      order by t.id
      """)
  List<TableEntity> findFiltered(
      @Param("branch") @Nullable String branch,
      @Param("zone") @Nullable String zone,
      @Param("status") @Nullable String status,
      @Param("search") @Nullable String search,
      @Param("activeOnly") boolean activeOnly);

  @NativeQuery(
      value =
          """
      select t.status as status, count(t) as tables from store_table t join branch b on b.id = t.branch_id
      where (:branch is null or :branch = '' or lower(b.name) = lower(:branch))
        and t.active = true
      group by t.status
      """)
  List<StatusCount> countActiveByStatus(@Param("branch") @Nullable String branch);

  interface StatusCount {
    String getStatus();

    long getTables();
  }

  @NativeQuery(
      value =
          "select count(*) > 0 from store_table t join branch b on b.id = t.branch_id where lower(t.name) = lower(:name) and lower(b.name) = lower(:branch) and (:excludeId is null or t.id <> :excludeId)")
  boolean existsByNameAndBranch(
      @Param("name") String name,
      @Param("branch") String branch,
      @Param("excludeId") @Nullable Long excludeId);
}
