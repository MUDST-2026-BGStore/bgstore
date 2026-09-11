package com.chanakanlabs.bgstore.tables;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import org.hibernate.annotations.UpdateTimestamp;
import org.springframework.lang.Nullable;

/** JPA mapping for the migration-owned {@code store_table} table. */
@Entity
@Table(name = "store_table")
@SuppressWarnings("NullAway.Init")
class TableEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "id", nullable = false)
  private @Nullable Long id;

  @Column(name = "name", nullable = false, length = 100)
  private String name;

  @Column(name = "branch", nullable = false, length = 100)
  private String branch;

  @Column(name = "capacity", nullable = false)
  private int capacity;

  @Column(name = "shape", nullable = false, length = 16)
  private String shape;

  @Column(name = "status", nullable = false, length = 16)
  private String status;

  @Column(name = "active", nullable = false)
  private boolean active;

  @Column(name = "zone", nullable = false, length = 100)
  private String zone;

  @UpdateTimestamp
  @Column(name = "last_updated", nullable = false)
  private OffsetDateTime lastUpdated;

  protected TableEntity() {}

  TableEntity(@Nullable Long id) {
    this.id = id;
  }

  void apply(TableRecordData data) {
    name = data.name();
    branch = data.branch();
    capacity = data.capacity();
    shape = data.shape();
    status = data.status();
    active = data.active();
    zone = data.zone();
  }

  TableRecordData toRecord() {
    return new TableRecordData(
        id, name, branch, capacity, shape, status, active, zone, lastUpdated);
  }
}
