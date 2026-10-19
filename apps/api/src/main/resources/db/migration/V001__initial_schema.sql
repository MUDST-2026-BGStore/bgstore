-- Development baseline. Databases with the old migration history must be recreated.

-- Accounts represent persistent application profiles.
-- Authentication and credentials remain managed by the OIDC provider.
CREATE TABLE accounts
(
  id               UUID PRIMARY KEY DEFAULT uuidv7(),
  first_name       TEXT NOT NULL,
  last_name        TEXT NOT NULL,
  phone_number     VARCHAR(32) NOT NULL,
  preferred_locale VARCHAR(35) NOT NULL,
  preferences      JSONB NOT NULL DEFAULT '{}'::jsonb,
  created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT chk_accounts_preferences_object CHECK (jsonb_typeof(preferences)
    = 'object')
);

-- Links OIDC identities to local application accounts.
-- One account may have multiple identities.
CREATE TABLE account_identities
(
  issuer     TEXT NOT NULL,
  subject    VARCHAR(255) NOT NULL,
  account_id UUID NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
  linked_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY (issuer, subject),
  CONSTRAINT chk_account_identities_issuer CHECK (issuer <> ''),
  CONSTRAINT chk_account_identities_subject CHECK (subject <> '')
);

CREATE INDEX idx_account_identities_account_id
  ON account_identities(account_id);

-- Customers are business records.
-- An account may have a customer record, but walk-ins need no account.
CREATE TABLE customers
(
  id            UUID PRIMARY KEY DEFAULT uuidv7(),
  account_id    UUID UNIQUE REFERENCES accounts(id) ON DELETE SET NULL,
  contact_name  TEXT,
  contact_phone VARCHAR(32),
  contact_email TEXT,
  created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Branches are the operational scope of staff assignments and branch-owned records.
-- Every branch is in Thailand: opening hours are local wall-clock time and all
-- prices are THB minor units, so neither is stored per branch.
CREATE TABLE branches
(
  id         UUID PRIMARY KEY DEFAULT uuidv7(),
  name       VARCHAR(120) NOT NULL,
  address    VARCHAR(500) NOT NULL,
  phone_number VARCHAR(40),
  active       BOOLEAN      NOT NULL DEFAULT TRUE,
  created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
  CONSTRAINT chk_branches_name CHECK (name <> ''),
  CONSTRAINT chk_branches_address CHECK (address <> ''),
  CONSTRAINT chk_branches_phone CHECK (phone_number IS NULL OR phone_number <> '')
);

-- Opening hours are local shop time (Asia/Bangkok, no DST, so wall-clock rows
-- are unambiguous). Intraday periods never overlap; overnight windows split
-- across adjacent days, so a row always has opens_at < closes_at. Omitted days
-- are closed. day_of_week uses ISO numbering: Monday=1 through Sunday=7
-- (matches java.time.DayOfWeek; the API's MONDAY..SUNDAY names map 1:1).
CREATE TABLE branch_opening_hours
(
  branch_id   UUID NOT NULL REFERENCES branches(id) ON DELETE CASCADE,
  day_of_week SMALLINT NOT NULL,
  opens_at    TIME     NOT NULL,
  closes_at   TIME     NOT NULL,
  PRIMARY KEY (branch_id, day_of_week, opens_at),
  CONSTRAINT chk_branch_opening_hours_day CHECK (day_of_week BETWEEN 1 AND 7),
  CONSTRAINT chk_branch_opening_hours_window CHECK (opens_at < closes_at)
);

-- Explicit access for branch operators. Managers and administrators operate
-- across branches without assignment rows.
CREATE TABLE branch_staff_assignments
(
  branch_id          UUID NOT NULL REFERENCES branches(id) ON DELETE CASCADE,
  staff_subject      TEXT NOT NULL,
  granted_by         TEXT NOT NULL,
  granted_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY (branch_id, staff_subject),
  CONSTRAINT chk_branch_staff_assignments_staff CHECK (staff_subject <> ''),
  CONSTRAINT chk_branch_staff_assignments_granter CHECK (granted_by <> '')
);

CREATE INDEX idx_branch_staff_assignments_staff
  ON branch_staff_assignments(staff_subject);

-- The shared game catalog. One business, one catalog; games are not branch-scoped.
CREATE TABLE games
(
  id                UUID PRIMARY KEY DEFAULT uuidv7(),
  version           BIGINT NOT NULL DEFAULT 0,
  name              VARCHAR(200) NOT NULL,
  designer          VARCHAR(200),
  difficulty        VARCHAR(20),
  min_players       INT              NOT NULL,
  max_players       INT              NOT NULL,
  min_age           INT,
  play_minutes_min  INT              NOT NULL,
  play_minutes_max  INT              NOT NULL,
  teach_minutes     INT,
  setup_minutes     INT,
  active            BOOLEAN          NOT NULL DEFAULT TRUE,
  created_at        TIMESTAMPTZ      NOT NULL DEFAULT now(),
  updated_at        TIMESTAMPTZ      NOT NULL DEFAULT now(),
  CONSTRAINT chk_games_name CHECK (char_length(name) BETWEEN 1 AND 200),
  CONSTRAINT chk_games_designer CHECK (designer IS NULL OR designer <> ''),
  CONSTRAINT chk_games_difficulty CHECK (difficulty IS NULL OR difficulty IN ('EASY', 'MEDIUM', 'ADVANCED')),
  CONSTRAINT chk_games_players CHECK (max_players >= min_players),
  CONSTRAINT chk_games_min_age CHECK (min_age IS NULL OR min_age >= 0),
  CONSTRAINT chk_games_play_minutes CHECK (play_minutes_max >= play_minutes_min),
  CONSTRAINT chk_games_teach_minutes CHECK (teach_minutes IS NULL OR teach_minutes >= 0),
  CONSTRAINT chk_games_setup_minutes CHECK (setup_minutes IS NULL OR setup_minutes >= 0)
);

CREATE INDEX idx_games_name ON games(name);

-- Shopping taxonomy. Canonical English names; clients render localized labels.
-- Names are stable join keys for filtering and a game may belong to many of each.
CREATE TABLE game_categories
(
  id   BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  version BIGINT NOT NULL DEFAULT 0,
  name VARCHAR(100) NOT NULL,
  CONSTRAINT chk_game_categories_name CHECK (name <> ''),
  CONSTRAINT uq_game_categories_name UNIQUE (name)
);

CREATE TABLE game_mechanics
(
  id   BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  version BIGINT NOT NULL DEFAULT 0,
  name VARCHAR(100) NOT NULL,
  CONSTRAINT chk_game_mechanics_name CHECK (name <> ''),
  CONSTRAINT uq_game_mechanics_name UNIQUE (name)
);

CREATE TABLE game_category_links
(
  game_id     UUID NOT NULL REFERENCES games(id) ON DELETE CASCADE,
  category_id BIGINT NOT NULL REFERENCES game_categories(id) ON DELETE CASCADE,
  PRIMARY KEY (game_id, category_id)
);

CREATE INDEX idx_game_category_links_category
  ON game_category_links(category_id);

CREATE TABLE game_mechanic_links
(
  game_id    UUID NOT NULL REFERENCES games(id) ON DELETE CASCADE,
  mechanic_id BIGINT NOT NULL REFERENCES game_mechanics(id) ON DELETE CASCADE,
  PRIMARY KEY (game_id, mechanic_id)
);

CREATE INDEX idx_game_mechanic_links_mechanic
  ON game_mechanic_links(mechanic_id);

-- Localized descriptions keyed by IETF locale tag, e.g. en-US, th-TH.
-- Proper names are never localized; only descriptions.
CREATE TABLE game_descriptions
(
  game_id     UUID NOT NULL REFERENCES games(id) ON DELETE CASCADE,
  locale      VARCHAR(35) NOT NULL,
  description TEXT        NOT NULL,
  PRIMARY KEY (game_id, locale),
  CONSTRAINT chk_game_descriptions_locale CHECK (char_length(locale) BETWEEN 1 AND 35),
  CONSTRAINT chk_game_descriptions_text CHECK (description <> '' AND char_length(description) <= 4000)
);

-- Localized taxonomy display names. The parent tables' `name` stays the
-- canonical English key used for filtering, joining and ordering; translation
-- rows never participate in filters. Every name should carry an en-US row.
CREATE TABLE game_category_names
(
  category_id BIGINT NOT NULL REFERENCES game_categories(id) ON DELETE CASCADE,
  locale      VARCHAR(35) NOT NULL,
  name        VARCHAR(150) NOT NULL,
  PRIMARY KEY (category_id, locale),
  CONSTRAINT chk_game_category_names_locale CHECK (char_length(locale) BETWEEN 1 AND 35),
  CONSTRAINT chk_game_category_names_name CHECK (name <> '')
);

CREATE TABLE game_mechanic_names
(
  mechanic_id BIGINT NOT NULL REFERENCES game_mechanics(id) ON DELETE CASCADE,
  locale      VARCHAR(35) NOT NULL,
  name        VARCHAR(150) NOT NULL,
  PRIMARY KEY (mechanic_id, locale),
  CONSTRAINT chk_game_mechanic_names_locale CHECK (char_length(locale) BETWEEN 1 AND 35),
  CONSTRAINT chk_game_mechanic_names_name CHECK (name <> '')
);

-- Approved product media. URLs point at store-managed assets.
CREATE TABLE game_images
(
  id         UUID PRIMARY KEY DEFAULT uuidv7(),
  game_id    UUID NOT NULL REFERENCES games(id) ON DELETE CASCADE,
  url        TEXT    NOT NULL,
  alt_text   VARCHAR(200),
  sort_order INT     NOT NULL DEFAULT 0,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT chk_game_images_url CHECK (url <> ''),
  CONSTRAINT uq_game_images_sort UNIQUE (game_id, sort_order)
);

-- Sellable editions of a game. An edition is the retail SKU: the unit that owns
-- per-branch sale stock, prices, cart lines and order items. Rental library
-- membership and rental copies stay game-scoped and are deliberately separate.
CREATE TABLE game_editions
(
  version       BIGINT NOT NULL DEFAULT 0,
  id            UUID PRIMARY KEY DEFAULT uuidv7(),
  game_id       UUID NOT NULL REFERENCES games(id) ON DELETE CASCADE,
  kind          VARCHAR(20) NOT NULL,
  language_code VARCHAR(35) NOT NULL,
  active        BOOLEAN NOT NULL DEFAULT TRUE,
  created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT chk_game_editions_kind CHECK (kind IN ('BASE_GAME', 'EXPANSION')),
  CONSTRAINT chk_game_editions_language CHECK (language_code <> ''),
  CONSTRAINT uq_game_editions_natural UNIQUE (game_id, kind, language_code)
);

CREATE INDEX idx_game_editions_game ON game_editions(game_id);

-- Per-branch offering configuration for a catalog game.
-- Disabling an offering stops new commitments while existing ones stay intact.
CREATE TABLE branch_games
(
  branch_id       UUID NOT NULL REFERENCES branches(id) ON DELETE CASCADE,
  game_id         UUID NOT NULL REFERENCES games(id) ON DELETE CASCADE,
  sale_enabled    BOOLEAN NOT NULL DEFAULT FALSE,
  rental_enabled  BOOLEAN NOT NULL DEFAULT FALSE,
  staff_can_teach BOOLEAN NOT NULL DEFAULT FALSE,
  rental_hourly_rate_minor BIGINT NOT NULL DEFAULT 0,
  created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY (branch_id, game_id),
  CONSTRAINT chk_branch_games_rate CHECK (rental_hourly_rate_minor >= 0)
);

CREATE INDEX idx_branch_games_game ON branch_games(game_id);

-- Per-branch sale stock and price for one edition of one game.
-- Prices are integer minor units (satang); THB is the only business currency,
-- so no currency column is stored. Rental copies and sale stock are separate
-- pools; this table never supplies it and rental_copies never supplies this.
CREATE TABLE branch_editions
(
  branch_id         UUID    NOT NULL,
  edition_id        UUID    NOT NULL REFERENCES game_editions(id) ON DELETE CASCADE,
  sale_price_minor  BIGINT  NOT NULL,
  stock_on_hand     INT     NOT NULL DEFAULT 0,
  stock_held        INT     NOT NULL DEFAULT 0,
  updated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY (branch_id, edition_id),
  FOREIGN KEY (branch_id) REFERENCES branches(id) ON DELETE CASCADE,
  CONSTRAINT chk_branch_editions_price CHECK (sale_price_minor >= 0),
  CONSTRAINT chk_branch_editions_stock_nonnegative CHECK (stock_on_hand >= 0 AND stock_held >= 0),
  CONSTRAINT chk_branch_editions_held_not_over_on_hand CHECK (stock_held <= stock_on_hand)
);

CREATE INDEX idx_branch_editions_edition ON branch_editions(edition_id);

-- Append-only ledger of sale stock receipts and corrections.
CREATE TABLE sale_stock_adjustments
(
  id              UUID PRIMARY KEY DEFAULT uuidv7(),
  branch_id       UUID NOT NULL REFERENCES branches(id) ON DELETE CASCADE,
  edition_id      UUID NOT NULL REFERENCES game_editions(id) ON DELETE CASCADE,
  delta           INT NOT NULL,
  reason          VARCHAR(500) NOT NULL,
  operator_subject TEXT NOT NULL,
  created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT chk_sale_stock_adjustments_delta_nonzero CHECK (delta <> 0),
  CONSTRAINT chk_sale_stock_adjustments_reason CHECK (reason <> ''),
  CONSTRAINT chk_sale_stock_adjustments_operator CHECK (operator_subject <> ''),
  CONSTRAINT uq_sale_stock_adjustments_pair UNIQUE (branch_id, edition_id, created_at, id),
  FOREIGN KEY (branch_id, edition_id)
    REFERENCES branch_editions(branch_id, edition_id) ON DELETE CASCADE
);

CREATE INDEX idx_sale_stock_adjustments_pair
  ON sale_stock_adjustments(branch_id, edition_id, created_at);

-- Physical rental copies. A copy belongs to exactly one branch and one game,
-- and can never simultaneously exist in sale stock. Codes are unique per branch.
-- rentalEnabled on branch_games is the rental library membership flag.
CREATE TABLE rental_copies
(
  id                  UUID PRIMARY KEY DEFAULT uuidv7(),
  branch_id           UUID NOT NULL REFERENCES branches(id) ON DELETE CASCADE,
  game_id             UUID NOT NULL REFERENCES games(id) ON DELETE CASCADE,
  asset_code          VARCHAR(64) NOT NULL,
  operational_status  VARCHAR(20) NOT NULL DEFAULT 'USABLE',
  created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT chk_rental_copies_code CHECK (asset_code <> ''),
  CONSTRAINT chk_rental_copies_status
    CHECK (operational_status IN ('USABLE', 'OUT_OF_SERVICE', 'RETIRED')),
  -- Retired copies remain in history and cannot be reactivated; app-level check.
  CONSTRAINT uq_rental_copies_code UNIQUE (branch_id, asset_code)
);

-- Composite uniqueness lets allocations FK-copy the owning branch.
CREATE UNIQUE INDEX uq_rental_copies_branch_id
  ON rental_copies(branch_id, id);

CREATE INDEX idx_rental_copies_game ON rental_copies(branch_id, game_id);

-- Branch play tables. Labels are unique per branch. Taking a table out of service
-- or reducing seats below an existing commitment is rejected at the API layer.
CREATE TABLE branch_tables
(
  id                 UUID PRIMARY KEY DEFAULT uuidv7(),
  branch_id          UUID NOT NULL REFERENCES branches(id) ON DELETE CASCADE,
  label              VARCHAR(80) NOT NULL,
  seats              INT NOT NULL,
  hourly_rate_minor  BIGINT NOT NULL DEFAULT 0,
  operational_status VARCHAR(20) NOT NULL DEFAULT 'IN_SERVICE',
  created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT chk_branch_tables_label CHECK (label <> ''),
  CONSTRAINT chk_branch_tables_seats CHECK (seats >= 1),
  CONSTRAINT chk_branch_tables_rate CHECK (hourly_rate_minor >= 0),
  CONSTRAINT chk_branch_tables_status
    CHECK (operational_status IN ('IN_SERVICE', 'OUT_OF_SERVICE')),
  CONSTRAINT uq_branch_tables_label UNIQUE (branch_id, label)
);

-- Composite uniqueness lets reservation FKs keep table and branch aligned.
CREATE UNIQUE INDEX uq_branch_tables_branch_id
  ON branch_tables(branch_id, id);

-- Table reservations. Booking windows are [startsAt, endsAt); the start is
-- inclusive, the end exclusive and strictly after the start. Guests have no
-- customer row; staff bookings and walk-ins snapshot guest contact fields.
-- Rates are locked at creation/reschedule as integer minor units (satang).
CREATE TABLE reservations
(
  id                      UUID PRIMARY KEY DEFAULT uuidv7(),
  branch_id               UUID NOT NULL REFERENCES branches(id) ON DELETE CASCADE,
  customer_id             UUID REFERENCES customers(id) ON DELETE SET NULL,
  guest_name              VARCHAR(150),
  guest_phone             VARCHAR(32),
  source                  VARCHAR(20) NOT NULL,
  status                  VARCHAR(20) NOT NULL DEFAULT 'CONFIRMED',
  starts_at               TIMESTAMPTZ NOT NULL,
  ends_at                 TIMESTAMPTZ NOT NULL,
  party_size              INT NOT NULL,
  table_id                UUID NOT NULL,
  table_hourly_rate_minor BIGINT NOT NULL,
  notes                   VARCHAR(1000),
  created_by_subject      TEXT NOT NULL,
  checked_in_at           TIMESTAMPTZ,
  checked_in_by           TEXT,
  cancelled_at            TIMESTAMPTZ,
  cancelled_by            TEXT,
  cancellation_reason     VARCHAR(500),
  no_show_at              TIMESTAMPTZ,
  no_show_by              TEXT,
  created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT fk_reservations_branch_table FOREIGN KEY (branch_id, table_id)
    REFERENCES branch_tables(branch_id, id),
  CONSTRAINT chk_reservations_source CHECK (source IN ('CUSTOMER', 'STAFF_BOOKING', 'WALK_IN')),
  CONSTRAINT chk_reservations_status
    CHECK (status IN ('CONFIRMED', 'CANCELLED', 'CHECKED_IN', 'NO_SHOW', 'COMPLETED')),
  CONSTRAINT chk_reservations_window CHECK (ends_at > starts_at),
  CONSTRAINT chk_reservations_party_size CHECK (party_size >= 1),
  CONSTRAINT chk_reservations_party_size_max CHECK (party_size <= 20),
  CONSTRAINT chk_reservations_subject CHECK (created_by_subject <> ''),
  CONSTRAINT chk_reservations_owner
    CHECK (customer_id IS NOT NULL OR (guest_name IS NOT NULL AND guest_name <> '')),
  CONSTRAINT chk_reservations_checked_in CHECK ((status = 'CHECKED_IN') = (checked_in_at IS NOT NULL)),
  CONSTRAINT chk_reservations_cancelled CHECK ((status = 'CANCELLED') = (cancelled_at IS NOT NULL)),
  CONSTRAINT chk_reservations_no_show CHECK ((status = 'NO_SHOW') = (no_show_at IS NOT NULL))
);

CREATE UNIQUE INDEX uq_reservations_branch_id ON reservations(branch_id, id);

CREATE INDEX idx_reservations_branch_start
  ON reservations(branch_id, starts_at);
CREATE INDEX idx_reservations_customer ON reservations(customer_id);
CREATE INDEX idx_reservations_table_interval
  ON reservations(table_id, starts_at, ends_at) WHERE status = 'CONFIRMED';

-- Selected titles with locked per-game rates. At most one row per game
-- (enforced by the primary key); an empty selection means table-only play.
CREATE TABLE reservation_games
(
  reservation_id          UUID NOT NULL REFERENCES reservations(id) ON DELETE CASCADE,
  game_id                 UUID NOT NULL REFERENCES games(id) ON DELETE RESTRICT,
  quantity                INT NOT NULL,
  unit_hourly_rate_minor  BIGINT NOT NULL,
  PRIMARY KEY (reservation_id, game_id),
  CONSTRAINT chk_reservation_games_quantity CHECK (quantity BETWEEN 1 AND 100)
);

-- The rental created by check-in. Exactly one rental per reservation.
-- Rates were locked by the reservation; checkout records actual charges.
CREATE TABLE rentals
(
  id                 UUID PRIMARY KEY DEFAULT uuidv7(),
  branch_id          UUID NOT NULL REFERENCES branches(id) ON DELETE CASCADE,
  reservation_id     UUID NOT NULL UNIQUE,
  status             VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
  started_at         TIMESTAMPTZ NOT NULL,
  planned_ends_at    TIMESTAMPTZ NOT NULL,
  actual_ends_at     TIMESTAMPTZ,
  checked_in_by      TEXT NOT NULL,
  checked_out_by     TEXT,
  total_minor        BIGINT,
  created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT fk_rentals_branch_reservation FOREIGN KEY (branch_id, reservation_id)
    REFERENCES reservations(branch_id, id) ON DELETE RESTRICT,
  CONSTRAINT chk_rentals_status CHECK (status IN ('ACTIVE', 'COMPLETED')),
  CONSTRAINT chk_rentals_completed
    CHECK ((status = 'COMPLETED') = (actual_ends_at IS NOT NULL AND checked_out_by IS NOT NULL AND total_minor IS NOT NULL)),
  CONSTRAINT chk_rentals_checked_out_by CHECK (checked_out_by IS NULL OR checked_out_by <> '')
);

-- Composite uniqueness keeps allocations in the same branch as their rental.
CREATE UNIQUE INDEX uq_rentals_branch_id ON rentals(branch_id, id);

-- One table charge and one charge per selected title. billableHours uses the
-- started-hour rule with a one-hour minimum: max(1, ceil(seconds / 3600)).
CREATE TABLE rental_charges
(
  id                     UUID PRIMARY KEY DEFAULT uuidv7(),
  rental_id              UUID NOT NULL REFERENCES rentals(id) ON DELETE CASCADE,
  charge_type            VARCHAR(10) NOT NULL,
  game_id                UUID REFERENCES games(id) ON DELETE RESTRICT,
  quantity               INT NOT NULL,
  unit_hourly_rate_minor BIGINT NOT NULL,
  billable_hours         INT NOT NULL,
  line_total_minor       BIGINT NOT NULL,
  CONSTRAINT chk_rental_charges_type CHECK (charge_type IN ('TABLE', 'GAME')),
  CONSTRAINT chk_rental_charges_table_line
    CHECK (charge_type <> 'TABLE' OR (game_id IS NULL AND quantity = 1)),
  CONSTRAINT chk_rental_charges_game_line
    CHECK (charge_type <> 'GAME' OR game_id IS NOT NULL),
  CONSTRAINT chk_rental_charges_positive CHECK (quantity >= 1 AND billable_hours >= 1),
  CONSTRAINT chk_rental_charges_amounts CHECK (unit_hourly_rate_minor >= 0 AND line_total_minor >= 0)
);

-- Exactly one table charge per rental and one charge per selected game.
CREATE UNIQUE INDEX uq_rental_charges_table
  ON rental_charges(rental_id) WHERE charge_type = 'TABLE';
CREATE UNIQUE INDEX uq_rental_charges_game
  ON rental_charges(rental_id, game_id) WHERE charge_type = 'GAME';

-- Copy allocations. Copies are located via (branch_id, copy_id) so an allocation
-- can never reference a copy owned by another branch. Physical return of every
-- copy is confirmed by checkout, not by this table.
CREATE TABLE rental_copy_allocations
(
  rental_id UUID NOT NULL,
  branch_id UUID NOT NULL,
  copy_id   UUID NOT NULL,
  PRIMARY KEY (rental_id, copy_id),
  CONSTRAINT fk_allocations_branch_rental FOREIGN KEY (branch_id, rental_id) REFERENCES rentals(branch_id, id) ON DELETE CASCADE,
  FOREIGN KEY (branch_id, copy_id) REFERENCES rental_copies(branch_id, id) ON DELETE RESTRICT
);

CREATE INDEX idx_rental_copy_allocations_copy
  ON rental_copy_allocations(branch_id, copy_id);

-- Game sales. PLACED orders hold stock (branch_editions.stock_held); FULFILLED
-- decrements both on-hand and held. CANCELLED releases holds. Totals are
-- server-calculated snapshot sums in THB minor units; prices are locked per line.
-- Pickup orders are fulfilled by the branch that owns the order; delivery orders
-- are fulfilled by the designated delivery branch.
CREATE TABLE orders
(
  id                  UUID PRIMARY KEY DEFAULT uuidv7(),
  branch_id           UUID NOT NULL REFERENCES branches(id) ON DELETE RESTRICT,
  customer_id         UUID REFERENCES customers(id) ON DELETE SET NULL,
  contact_name        VARCHAR(150) NOT NULL,
  contact_email       VARCHAR(254) NOT NULL,
  contact_phone       VARCHAR(32)  NOT NULL,
  source              VARCHAR(20) NOT NULL,
  status              VARCHAR(20) NOT NULL DEFAULT 'PLACED',
  fulfillment_method  VARCHAR(10) NOT NULL,
  shipping_address    JSONB,
  notes               VARCHAR(1000),
  total_minor         BIGINT NOT NULL,
  created_by_subject  TEXT NOT NULL,
  fulfilled_at        TIMESTAMPTZ,
  fulfilled_by        TEXT,
  cancelled_at        TIMESTAMPTZ,
  cancelled_by        TEXT,
  cancellation_reason VARCHAR(500),
  created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT chk_orders_contact_name CHECK (contact_name <> ''),
  CONSTRAINT chk_orders_contact_email CHECK (contact_email <> ''),
  CONSTRAINT chk_orders_contact_phone CHECK (contact_phone <> ''),
  CONSTRAINT chk_orders_source CHECK (source IN ('CUSTOMER', 'STAFF_COUNTER')),
  CONSTRAINT chk_orders_status CHECK (status IN ('PLACED', 'CANCELLED', 'FULFILLED')),
  CONSTRAINT chk_orders_fulfillment CHECK (fulfillment_method IN ('PICKUP', 'SHIPPING')),
  CONSTRAINT chk_orders_shipping_address
    CHECK (fulfillment_method <> 'SHIPPING'
           OR (shipping_address IS NOT NULL AND jsonb_typeof(shipping_address) = 'object')),
  CONSTRAINT chk_orders_total CHECK (total_minor >= 0),
  CONSTRAINT chk_orders_subject CHECK (created_by_subject <> ''),
  CONSTRAINT chk_orders_fulfilled CHECK ((status = 'FULFILLED') = (fulfilled_at IS NOT NULL)),
  CONSTRAINT chk_orders_cancelled
    CHECK ((status = 'CANCELLED') = (cancelled_at IS NOT NULL AND cancellation_reason IS NOT NULL))
);

CREATE INDEX idx_orders_branch_status ON orders(branch_id, status);
CREATE INDEX idx_orders_customer ON orders(customer_id);

-- Line snapshots: the edition owns current stock/price, while the game name and
-- unit price are frozen into the order for history. One line per edition.
CREATE TABLE order_items
(
  order_id        UUID NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
  edition_id      UUID NOT NULL REFERENCES game_editions(id) ON DELETE RESTRICT,
  game_name       VARCHAR(200) NOT NULL,
  quantity        INT NOT NULL,
  unit_price_minor BIGINT NOT NULL,
  line_total_minor BIGINT NOT NULL,
  PRIMARY KEY (order_id, edition_id),
  CONSTRAINT chk_order_items_name CHECK (game_name <> ''),
  CONSTRAINT chk_order_items_quantity CHECK (quantity BETWEEN 1 AND 100),
  CONSTRAINT chk_order_items_amounts CHECK (unit_price_minor >= 0 AND line_total_minor >= 0),
  CONSTRAINT chk_order_items_math CHECK (line_total_minor = quantity * unit_price_minor)
);

-- Persistent server carts. A cart does not hold or reserve stock; prices,
-- currency and availability are revalidated against the selected fulfillment
-- location whenever the cart is read and again at checkout. Local preview data
-- in the browser is never authoritative.
--
-- Ownership: guests authenticate with a one-time random cart token (stored
-- hashed); the token is returned only at creation. When its owner signs in and
-- merges it, the merged cart is bound to the account and the token is consumed.
CREATE TABLE carts
(
  id                 UUID PRIMARY KEY DEFAULT uuidv7(),
  account_id         UUID REFERENCES accounts(id) ON DELETE CASCADE,
  token_hash         BYTEA NOT NULL,
  status             VARCHAR(20) NOT NULL DEFAULT 'OPEN',
  fulfillment_method VARCHAR(10) NOT NULL DEFAULT 'PICKUP',
  pickup_branch_id   UUID REFERENCES branches(id) ON DELETE SET NULL,
  created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT chk_carts_token_hash CHECK (octet_length(token_hash) = 32),
  CONSTRAINT chk_carts_status CHECK (status IN ('OPEN', 'CONVERTED', 'ABANDONED')),
  CONSTRAINT chk_carts_fulfillment CHECK (fulfillment_method IN ('PICKUP', 'SHIPPING')),
  -- Pickup needs an explicit branch; delivery ignores any stored one.
  CONSTRAINT chk_carts_pickup_branch
    CHECK ((fulfillment_method = 'PICKUP') = (pickup_branch_id IS NOT NULL))
);

-- At most one open cart per account.
CREATE UNIQUE INDEX uq_carts_account_open
  ON carts(account_id) WHERE account_id IS NOT NULL AND status = 'OPEN';
CREATE INDEX idx_carts_token_hash ON carts(token_hash);

-- One line per edition; quantities are bounded like order quantities.
CREATE TABLE cart_items
(
  cart_id    UUID NOT NULL REFERENCES carts(id) ON DELETE CASCADE,
  edition_id UUID NOT NULL REFERENCES game_editions(id) ON DELETE RESTRICT,
  quantity   INT NOT NULL,
  added_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY (cart_id, edition_id),
  CONSTRAINT chk_cart_items_quantity CHECK (quantity BETWEEN 1 AND 99)
);

-- Reservation and order creation require a caller-generated UUID retained for
-- at least 24 hours. Keys are scoped by authenticated subject, HTTP method and
-- concrete path. The request body hash distinguishes replays: the same key and
-- body returns the original result, a different body conflicts (409).
-- resource_id records what the key created so a replay can return it.
-- Cleanup of keys older than 24 hours is an operational job, not part of writes.
CREATE TABLE idempotency_keys
(
  subject         TEXT NOT NULL,
  http_method     VARCHAR(10) NOT NULL,
  path            TEXT NOT NULL,
  idempotency_key UUID NOT NULL,
  request_hash    BYTEA NOT NULL,
  resource_id     UUID,
  created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY (subject, http_method, path, idempotency_key),
  CONSTRAINT chk_idempotency_keys_subject CHECK (subject <> ''),
  CONSTRAINT chk_idempotency_keys_path CHECK (path <> ''),
  CONSTRAINT chk_idempotency_keys_request_hash CHECK (octet_length(request_hash) > 0)
);
