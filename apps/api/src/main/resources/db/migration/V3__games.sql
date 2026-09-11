CREATE TABLE branch (
  id UUID PRIMARY KEY,
  name VARCHAR(120) NOT NULL UNIQUE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO branch (id, name)
VALUES
  ('3f0d7d5a-9a2b-4a71-8f0e-000000000001', 'Central Rama II'),
  ('3f0d7d5a-9a2b-4a71-8f0e-000000000002', 'Big C Rama I'),
  ('3f0d7d5a-9a2b-4a71-8f0e-000000000003', 'Big C Rama IX'),
  ('3f0d7d5a-9a2b-4a71-8f0e-000000000004', 'Sukhumvit'),
  ('3f0d7d5a-9a2b-4a71-8f0e-000000000005', 'Silom'),
  ('3f0d7d5a-9a2b-4a71-8f0e-000000000006', 'Thonglor');

CREATE TABLE game (
  id UUID PRIMARY KEY,
  title VARCHAR(120) NOT NULL,
  description VARCHAR(160),
  category VARCHAR(16) NOT NULL,
  min_players INTEGER NOT NULL,
  max_players INTEGER NOT NULL,
  play_time_minutes INTEGER,
  difficulty VARCHAR(60),
  tags VARCHAR(40)[] NOT NULL DEFAULT ARRAY[]::VARCHAR[],
  lifecycle VARCHAR(16) NOT NULL DEFAULT 'active',
  last_played_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT game_category_known CHECK (category IN ('family', 'card', 'party', 'strategy')),
  CONSTRAINT game_lifecycle_known CHECK (lifecycle IN ('active', 'retired')),
  CONSTRAINT game_title_not_blank CHECK (length(btrim(title)) > 0),
  CONSTRAINT game_min_players_range CHECK (min_players BETWEEN 1 AND 99),
  CONSTRAINT game_max_players_range CHECK (max_players BETWEEN 1 AND 99),
  CONSTRAINT game_player_range CHECK (max_players >= min_players),
  CONSTRAINT game_play_time_range CHECK (play_time_minutes IS NULL OR play_time_minutes BETWEEN 1 AND 600)
);

CREATE INDEX game_title_folded_idx ON game (lower(title));

CREATE TABLE game_branch_stock (
  game_id UUID NOT NULL REFERENCES game (id) ON DELETE CASCADE,
  branch_id UUID NOT NULL REFERENCES branch (id),
  copies INTEGER NOT NULL,
  copies_in_use INTEGER NOT NULL DEFAULT 0,
  PRIMARY KEY (game_id, branch_id),
  CONSTRAINT game_branch_stock_copies_range CHECK (copies BETWEEN 0 AND 999),
  CONSTRAINT game_branch_stock_in_use_range CHECK (copies_in_use BETWEEN 0 AND copies)
);

CREATE INDEX game_branch_stock_branch_idx ON game_branch_stock (branch_id);
