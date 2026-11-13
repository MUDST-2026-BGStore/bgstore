-- Physical copies held for a reservation. A hold commits its copy for the
-- reservation's [starts_at, ends_at) while the reservation is CONFIRMED; check-in
-- turns holds into rental_copy_allocations. Copies are located via
-- (branch_id, copy_id) so a hold can never reference another branch's copy.
-- Writers must lock the copy rows they hold so two overlapping bookings, or a
-- booking and a condition change, cannot commit the same copy.
CREATE TABLE reservation_copy_holds
(
  reservation_id UUID NOT NULL,
  branch_id      UUID NOT NULL,
  copy_id        UUID NOT NULL,
  PRIMARY KEY (reservation_id, copy_id),
  CONSTRAINT fk_copy_holds_branch_reservation FOREIGN KEY (branch_id, reservation_id)
    REFERENCES reservations(branch_id, id) ON DELETE CASCADE,
  FOREIGN KEY (branch_id, copy_id) REFERENCES rental_copies(branch_id, id) ON DELETE RESTRICT
);

CREATE INDEX idx_reservation_copy_holds_copy
  ON reservation_copy_holds(branch_id, copy_id);
