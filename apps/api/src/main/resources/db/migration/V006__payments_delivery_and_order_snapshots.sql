-- At most one branch ships delivery orders; it is the fulfillment location of
-- every SHIPPING cart and order.
ALTER TABLE branches ADD COLUMN fulfills_delivery BOOLEAN NOT NULL DEFAULT FALSE;
CREATE UNIQUE INDEX uq_branches_delivery ON branches(fulfills_delivery) WHERE fulfills_delivery;

-- Counter sales to walk-in guests may have no email or phone.
ALTER TABLE orders ALTER COLUMN contact_email DROP NOT NULL;
ALTER TABLE orders ALTER COLUMN contact_phone DROP NOT NULL;

-- The registered customer's identity subject at placement; NULL for guests.
-- It answers "whose order is this" without resolving customers to accounts.
ALTER TABLE orders ADD COLUMN customer_subject TEXT;
ALTER TABLE orders ADD CONSTRAINT chk_orders_customer_subject
  CHECK (customer_subject IS NULL OR customer_subject <> '');
CREATE INDEX idx_orders_customer_subject ON orders(customer_subject, id)
  WHERE customer_subject IS NOT NULL;

-- The edition's component language is part of the line snapshot.
ALTER TABLE order_items ADD COLUMN edition_language VARCHAR(35) NOT NULL DEFAULT 'und';
ALTER TABLE order_items ALTER COLUMN edition_language DROP DEFAULT;
ALTER TABLE order_items ADD CONSTRAINT chk_order_items_language CHECK (edition_language <> '');

-- One payment per order. CARD payments are authorized through a gateway at
-- placement, captured at fulfillment and voided on cancellation. COUNTER
-- payments are settled by staff: PENDING until handover. Card numbers and
-- security codes are never stored; only the brand and last four digits are.
CREATE TABLE payments
(
  id                UUID PRIMARY KEY DEFAULT uuidv7(),
  order_id          UUID NOT NULL UNIQUE REFERENCES orders(id) ON DELETE RESTRICT,
  method            VARCHAR(20) NOT NULL,
  status            VARCHAR(20) NOT NULL,
  amount_minor      BIGINT NOT NULL,
  gateway           VARCHAR(40),
  gateway_reference TEXT,
  card_brand        VARCHAR(20),
  card_last4        VARCHAR(4),
  created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT chk_payments_method CHECK (method IN ('CARD', 'COUNTER')),
  CONSTRAINT chk_payments_status
    CHECK (status IN ('PENDING', 'AUTHORIZED', 'CAPTURED', 'VOIDED')),
  CONSTRAINT chk_payments_amount CHECK (amount_minor >= 0),
  CONSTRAINT chk_payments_card
    CHECK ((method = 'CARD') = (gateway IS NOT NULL AND gateway_reference IS NOT NULL
                                AND card_brand IS NOT NULL AND card_last4 IS NOT NULL)),
  CONSTRAINT chk_payments_card_last4 CHECK (card_last4 IS NULL OR card_last4 ~ '^[0-9]{4}$'),
  CONSTRAINT chk_payments_method_status
    CHECK ((method = 'CARD' AND status <> 'PENDING') OR (method = 'COUNTER' AND status <> 'AUTHORIZED'))
);

-- Single-use card tokens issued by the development gateway. A token remembers
-- how its card behaves (approve, decline, error) but never the card itself.
CREATE TABLE bogus_payment_tokens
(
  token_hash  BYTEA PRIMARY KEY,
  card_brand  VARCHAR(20) NOT NULL,
  card_last4  VARCHAR(4) NOT NULL,
  behavior    VARCHAR(30) NOT NULL,
  expires_at  TIMESTAMPTZ NOT NULL,
  consumed_at TIMESTAMPTZ,
  created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT chk_bogus_payment_tokens_hash CHECK (octet_length(token_hash) = 32),
  CONSTRAINT chk_bogus_payment_tokens_last4 CHECK (card_last4 ~ '^[0-9]{4}$')
);
