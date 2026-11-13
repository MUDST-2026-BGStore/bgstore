-- A payment now pays for either an order or a reservation's food pre-order,
-- never both and never neither. Each reference keeps a real foreign key.
ALTER TABLE payments ALTER COLUMN order_id DROP NOT NULL;
ALTER TABLE payments ADD COLUMN reservation_id UUID UNIQUE REFERENCES reservations(id) ON DELETE RESTRICT;
ALTER TABLE payments ADD CONSTRAINT chk_payments_one_payable
  CHECK (num_nonnulls(order_id, reservation_id) = 1);

-- The business-wide food and drink menu. Names and descriptions are keyed by
-- IETF locale tag and always include en-US. One price applies at every branch.
CREATE TABLE menu_items
(
  id           UUID PRIMARY KEY DEFAULT uuidv7(),
  sku          VARCHAR(60) NOT NULL UNIQUE,
  category     VARCHAR(20) NOT NULL,
  names        JSONB NOT NULL,
  descriptions JSONB NOT NULL DEFAULT '{}'::jsonb,
  price_minor  BIGINT NOT NULL,
  dietary      VARCHAR(40),
  popular      BOOLEAN NOT NULL DEFAULT FALSE,
  active       BOOLEAN NOT NULL DEFAULT TRUE,
  created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT chk_menu_items_sku CHECK (sku <> ''),
  CONSTRAINT chk_menu_items_category
    CHECK (category IN ('BEVERAGES', 'SNACKS', 'TEA_COFFEE', 'BAKERY_DESSERTS')),
  CONSTRAINT chk_menu_items_names
    CHECK (jsonb_typeof(names) = 'object' AND names ? 'en-US'),
  CONSTRAINT chk_menu_items_descriptions CHECK (jsonb_typeof(descriptions) = 'object'),
  CONSTRAINT chk_menu_items_price CHECK (price_minor >= 0),
  CONSTRAINT chk_menu_items_dietary CHECK (dietary IS NULL OR dietary <> '')
);

-- Per-branch pantry stock, with the same hold / release / fulfil rules as game
-- sale stock: pre-orders of confirmed reservations hold units, check-in
-- consumes them, cancellation and no-shows release them.
CREATE TABLE branch_menu_stock
(
  branch_id    UUID NOT NULL REFERENCES branches(id) ON DELETE CASCADE,
  menu_item_id UUID NOT NULL REFERENCES menu_items(id) ON DELETE CASCADE,
  on_hand      INT NOT NULL DEFAULT 0,
  held         INT NOT NULL DEFAULT 0,
  updated_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY (branch_id, menu_item_id),
  CONSTRAINT chk_branch_menu_stock_nonnegative CHECK (on_hand >= 0 AND held >= 0),
  CONSTRAINT chk_branch_menu_stock_held_not_over_on_hand CHECK (held <= on_hand)
);

CREATE INDEX idx_branch_menu_stock_item ON branch_menu_stock(menu_item_id);

-- Append-only ledger of pantry receipts and corrections.
CREATE TABLE menu_stock_adjustments
(
  id               UUID PRIMARY KEY DEFAULT uuidv7(),
  branch_id        UUID NOT NULL,
  menu_item_id     UUID NOT NULL,
  delta            INT NOT NULL,
  reason           VARCHAR(500) NOT NULL,
  operator_subject TEXT NOT NULL,
  created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT chk_menu_stock_adjustments_delta CHECK (delta <> 0),
  CONSTRAINT chk_menu_stock_adjustments_reason CHECK (reason <> ''),
  CONSTRAINT chk_menu_stock_adjustments_operator CHECK (operator_subject <> ''),
  FOREIGN KEY (branch_id, menu_item_id)
    REFERENCES branch_menu_stock(branch_id, menu_item_id) ON DELETE CASCADE
);

CREATE INDEX idx_menu_stock_adjustments_pair
  ON menu_stock_adjustments(branch_id, menu_item_id, created_at);

-- Food and drink paid for with a reservation. Totals are integer THB minor
-- units: total = subtotal - discount, the discount fixed at booking.
CREATE TABLE reservation_preorders
(
  reservation_id  UUID PRIMARY KEY REFERENCES reservations(id) ON DELETE CASCADE,
  subtotal_minor  BIGINT NOT NULL,
  discount_minor  BIGINT NOT NULL,
  total_minor     BIGINT NOT NULL,
  created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT chk_reservation_preorders_amounts
    CHECK (subtotal_minor > 0 AND discount_minor >= 0 AND discount_minor <= subtotal_minor
           AND total_minor = subtotal_minor - discount_minor)
);

-- Pre-ordered lines with name and price snapshots from booking.
CREATE TABLE reservation_preorder_items
(
  reservation_id   UUID NOT NULL REFERENCES reservation_preorders(reservation_id) ON DELETE CASCADE,
  menu_item_id     UUID NOT NULL REFERENCES menu_items(id) ON DELETE RESTRICT,
  name             VARCHAR(150) NOT NULL,
  quantity         INT NOT NULL,
  unit_price_minor BIGINT NOT NULL,
  line_total_minor BIGINT NOT NULL,
  PRIMARY KEY (reservation_id, menu_item_id),
  CONSTRAINT chk_reservation_preorder_items_name CHECK (name <> ''),
  CONSTRAINT chk_reservation_preorder_items_quantity CHECK (quantity BETWEEN 1 AND 50),
  CONSTRAINT chk_reservation_preorder_items_price CHECK (unit_price_minor >= 0),
  CONSTRAINT chk_reservation_preorder_items_total
    CHECK (line_total_minor = unit_price_minor * quantity)
);
