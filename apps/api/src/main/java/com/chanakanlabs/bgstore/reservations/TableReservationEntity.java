package com.chanakanlabs.bgstore.reservations;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import org.springframework.lang.Nullable;

/**
 * A reservation's hold on one table. The table is created by a Flyway migration.
 *
 * <p>The table is referenced by id rather than by a JPA association, because {@code store_table}
 * belongs to the tables module.
 */
@Entity
@Table(
    name = "table_reservation",
    indexes = @Index(name = "ix_table_reservation_table_ends", columnList = "table_id, ends_at"))
@SuppressWarnings("NullAway.Init")
class TableReservationEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "id", nullable = false)
  private @Nullable Long id;

  @Column(name = "table_id", nullable = false)
  private long tableId;

  @Column(name = "starts_at", nullable = false)
  private OffsetDateTime startsAt;

  @Column(name = "ends_at", nullable = false)
  private OffsetDateTime endsAt;

  protected TableReservationEntity() {}

  TableReservationEntity(long tableId, OffsetDateTime startsAt, OffsetDateTime endsAt) {
    this.tableId = tableId;
    this.startsAt = startsAt;
    this.endsAt = endsAt;
  }

  long tableId() {
    return tableId;
  }

  ReservedSlot toSlot() {
    return new ReservedSlot(startsAt, endsAt);
  }
}
