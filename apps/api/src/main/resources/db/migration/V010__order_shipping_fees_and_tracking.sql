ALTER TABLE orders
  ADD COLUMN shipping_fee_minor BIGINT NOT NULL DEFAULT 0,
  ADD COLUMN carrier VARCHAR(20),
  ADD COLUMN tracking_number VARCHAR(60),
  ADD CONSTRAINT chk_orders_shipping_fee CHECK (shipping_fee_minor >= 0),
  ADD CONSTRAINT chk_orders_carrier
    CHECK (carrier IS NULL OR carrier IN ('THAILAND_POST', 'KERRY', 'FLASH', 'JT')),
  ADD CONSTRAINT chk_orders_tracking_pair
    CHECK ((carrier IS NULL) = (tracking_number IS NULL)),
  ADD CONSTRAINT chk_orders_tracking_number
    CHECK (tracking_number IS NULL OR tracking_number <> ''),
  ADD CONSTRAINT chk_orders_pickup_shipping
    CHECK (fulfillment_method <> 'PICKUP'
           OR (shipping_fee_minor = 0 AND carrier IS NULL));
