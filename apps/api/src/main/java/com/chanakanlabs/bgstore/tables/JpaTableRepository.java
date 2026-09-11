package com.chanakanlabs.bgstore.tables;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.lang.Nullable;

/** Database adapter for the table-management repository port. */
interface JpaTableRepository extends JpaRepository<TableEntity, Long> {

  @Query(
      """
      select t from TableEntity t
      where (:branch is null or :branch = '' or lower(t.branch) = lower(:branch))
        and (:zone is null or :zone = '' or lower(:zone) = 'all zones' or lower(t.zone) = lower(:zone))
        and (:status is null or :status = '' or lower(:status) = 'all statuses' or lower(t.status) = lower(:status))
        and (:search is null or :search = '' or lower(t.name) like lower(concat('%', :search, '%'))
             or lower(t.zone) like lower(concat('%', :search, '%')))
      order by t.id
      """)
  List<TableEntity> findFiltered(
      @Param("branch") @Nullable String branch,
      @Param("zone") @Nullable String zone,
      @Param("status") @Nullable String status,
      @Param("search") @Nullable String search);

  @Query(
      "select count(t) > 0 from TableEntity t where lower(t.name) = lower(:name) and lower(t.branch) = lower(:branch) and (:excludeId is null or t.id <> :excludeId)")
  boolean existsByNameAndBranch(
      @Param("name") String name,
      @Param("branch") String branch,
      @Param("excludeId") @Nullable Long excludeId);
}
