-- The catalog game of each order line at placement, so order history can link to
-- the product. Nullable: lines written before this column have no recorded game.
ALTER TABLE order_items ADD COLUMN game_id UUID;
