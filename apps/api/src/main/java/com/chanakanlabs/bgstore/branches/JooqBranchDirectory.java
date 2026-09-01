package com.chanakanlabs.bgstore.branches;

import static com.chanakanlabs.bgstore.database.Tables.BRANCH;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional(readOnly = true)
class JooqBranchDirectory implements BranchDirectory {

  private final DSLContext database;

  JooqBranchDirectory(DSLContext database) {
    this.database = database;
  }

  @Override
  public List<Branch> findAll() {
    return database
        .select(BRANCH.ID, BRANCH.NAME)
        .from(BRANCH)
        .orderBy(BRANCH.NAME.asc())
        .fetch(JooqBranchDirectory::toBranch);
  }

  @Override
  public Optional<Branch> findById(UUID id) {
    return database
        .select(BRANCH.ID, BRANCH.NAME)
        .from(BRANCH)
        .where(BRANCH.ID.eq(id))
        .fetchOptional(JooqBranchDirectory::toBranch);
  }

  private static Branch toBranch(Record row) {
    return new Branch(row.get(BRANCH.ID), row.get(BRANCH.NAME));
  }
}
