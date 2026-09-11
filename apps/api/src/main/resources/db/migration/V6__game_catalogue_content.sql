-- Game catalogue photos and how-to-play content are optional extensions of the V5 game row.
ALTER TABLE game
    ADD COLUMN image_urls TEXT[],
    ADD COLUMN goal_en VARCHAR(600),
    ADD COLUMN goal_th VARCHAR(600),
    ADD COLUMN players_note_en VARCHAR(600),
    ADD COLUMN players_note_th VARCHAR(600),
    ADD COLUMN equipment_en VARCHAR(600),
    ADD COLUMN equipment_th VARCHAR(600);

CREATE TABLE game_guide_step (
    game_id UUID NOT NULL REFERENCES game (id) ON DELETE CASCADE,
    position INTEGER NOT NULL,
    title_en VARCHAR(120) NOT NULL,
    title_th VARCHAR(120),
    body_en VARCHAR(600),
    body_th VARCHAR(600),
    CONSTRAINT pk_game_guide_step PRIMARY KEY (game_id, position),
    CONSTRAINT ck_game_guide_step_position CHECK (position >= 0)
);
