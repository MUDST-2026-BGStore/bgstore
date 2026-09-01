package com.chanakanlabs.bgstore.branches;

import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.name;
import static org.jooq.impl.DSL.table;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.Table;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional(readOnly = true)
class JooqBranchDirectory implements BranchDirectory {

  private static final Table<?> BRANCH = table(name("branch"));
  private static final Field<UUID> ID = field(name("branch", "id"), UUID.class);
  private static final Field<String> NAME = field(name("branch", "name"), String.class);

  private final DSLContext database;

  JooqBranchDirectory(DSLContext database) {
    this.database = database;
  }

  @Override
  public List<Branch> findAll() {
    return database
        .select(ID, NAME)
        .from(BRANCH)
        .orderBy(NAME.asc())
        .fetch(JooqBranchDirectory::toBranch);
  }

  @Override
  public Optional<Branch> findById(UUID id) {
    return database
        .select(ID, NAME)
        .from(BRANCH)
        .where(ID.eq(id))
        .fetchOptional(JooqBranchDirectory::toBranch);
  }

  private static Branch toBranch(Record row) {
    return new Branch(row.get(ID), row.get(NAME));
  }
}
