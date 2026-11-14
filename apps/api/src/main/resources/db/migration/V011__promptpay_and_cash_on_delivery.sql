-- Two more ways to pay a retail order. PROMPTPAY is a QR charge at the
-- gateway: PENDING until the provider reports it paid, then CAPTURED, and it
-- expires at expires_at. COD (cash on delivery) never reaches a gateway: it
-- stays PENDING until staff confirm the cash was received. A PromptPay row keeps
-- the QR image the provider issued so the customer can be shown it again.
ALTER TABLE payments
  ADD COLUMN expires_at TIMESTAMPTZ,
  ADD COLUMN promptpay_qr TEXT;

ALTER TABLE payments
  DROP CONSTRAINT chk_payments_method,
  ADD CONSTRAINT chk_payments_method CHECK (method IN ('CARD', 'COUNTER', 'PROMPTPAY', 'COD')),
  DROP CONSTRAINT chk_payments_card,
  ADD CONSTRAINT chk_payments_card CHECK (
    CASE method
      WHEN 'CARD' THEN gateway IS NOT NULL AND gateway_reference IS NOT NULL
                       AND card_brand IS NOT NULL AND card_last4 IS NOT NULL
      WHEN 'PROMPTPAY' THEN gateway IS NOT NULL AND gateway_reference IS NOT NULL
                            AND card_brand IS NULL AND card_last4 IS NULL
      ELSE gateway IS NULL AND gateway_reference IS NULL
           AND card_brand IS NULL AND card_last4 IS NULL
    END),
  DROP CONSTRAINT chk_payments_method_status,
  ADD CONSTRAINT chk_payments_method_status CHECK (
    (method = 'CARD' AND status <> 'PENDING') OR (method <> 'CARD' AND status <> 'AUTHORIZED')),
  ADD CONSTRAINT chk_payments_expiry CHECK ((method = 'PROMPTPAY') = (expires_at IS NOT NULL)),
  ADD CONSTRAINT chk_payments_promptpay CHECK ((method = 'PROMPTPAY') = (promptpay_qr IS NOT NULL)),
  ADD CONSTRAINT chk_payments_promptpay_qr CHECK (promptpay_qr IS NULL OR promptpay_qr <> '');

-- The expiry job looks for unpaid QR charges past their deadline.
CREATE INDEX idx_payments_pending_expiry ON payments (expires_at)
  WHERE method = 'PROMPTPAY' AND status = 'PENDING';

-- PromptPay charges issued by the development gateway. Nothing pays them on its
-- own; a test (or a developer) sets paid_at to simulate the customer paying.
CREATE TABLE bogus_promptpay_charges
(
  reference    TEXT PRIMARY KEY,
  amount_minor BIGINT NOT NULL,
  expires_at   TIMESTAMPTZ NOT NULL,
  paid_at      TIMESTAMPTZ,
  created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT chk_bogus_promptpay_charges_amount CHECK (amount_minor > 0)
);
