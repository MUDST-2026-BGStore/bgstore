-- Served refreshments are one-time counter charges, separate from hourly play fees.
-- Scalar FKs preserve module ownership; snapshots survive subsequent menu edits.
CREATE TABLE rental_refreshments
(
  id               UUID PRIMARY KEY DEFAULT uuidv7(),
  rental_id        UUID NOT NULL REFERENCES rentals(id) ON DELETE RESTRICT,
  menu_item_id     UUID NOT NULL REFERENCES menu_items(id) ON DELETE RESTRICT,
  name             VARCHAR(150) NOT NULL,
  quantity         INT NOT NULL,
  unit_price_minor BIGINT NOT NULL,
  line_total_minor BIGINT NOT NULL,
  served_at        TIMESTAMPTZ NOT NULL,
  served_by        TEXT NOT NULL,
  CONSTRAINT chk_rental_refreshments_name CHECK (name <> ''),
  CONSTRAINT chk_rental_refreshments_quantity CHECK (quantity BETWEEN 1 AND 50),
  CONSTRAINT chk_rental_refreshments_price CHECK (unit_price_minor >= 0),
  CONSTRAINT chk_rental_refreshments_total CHECK (line_total_minor = unit_price_minor * quantity),
  CONSTRAINT chk_rental_refreshments_operator CHECK (served_by <> '')
);
CREATE INDEX idx_rental_refreshments_rental ON rental_refreshments(rental_id);
