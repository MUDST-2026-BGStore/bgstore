ALTER TABLE game RENAME COLUMN title TO title_en;
ALTER TABLE game RENAME COLUMN description TO description_en;
ALTER TABLE game ADD COLUMN title_th VARCHAR(120);
ALTER TABLE game ADD COLUMN description_th VARCHAR(160);

ALTER TABLE game RENAME CONSTRAINT game_title_not_blank TO game_title_en_not_blank;
ALTER TABLE game
  ADD CONSTRAINT game_title_th_not_blank
  CHECK (title_th IS NULL OR length(btrim(title_th)) > 0);

DROP INDEX game_title_folded_idx;
CREATE INDEX game_title_en_folded_idx ON game (lower(title_en));
CREATE INDEX game_title_th_folded_idx ON game (lower(title_th));
