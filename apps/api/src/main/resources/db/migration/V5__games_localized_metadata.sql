-- Game titles and descriptions become language-aware catalogue metadata.
--
-- `docs/domain-model.md` already calls a game title "language-aware catalog
-- metadata"; this is the schema catching up. English is the canonical entry
-- every game carries, so the existing columns are renamed rather than replaced:
-- what is already in the catalogue was entered in English. Thai is added
-- alongside and stays optional. A reader resolves a locale by taking that
-- language when it holds text and falling back to English, which is the rule
-- `LocalizedTitle` publishes in the contract.
--
-- Appended rather than folded into V3, which is already applied wherever this
-- branch has been run.

alter table game rename column title to title_en;
alter table game rename column description to description_en;

alter table game add column title_th varchar(120);
alter table game add column description_th varchar(160);

-- Renaming the column carried the existing check over to `title_en`, so only
-- its name is stale. Thai gets the same rule: absent is null, never blank.
-- Constraint names are not something jOOQ generates from, and its DDL simulator
-- has no constraint to rename, so it skips this block.
-- [jooq ignore start]
alter table game rename constraint game_title_not_blank to game_title_en_not_blank;

alter table game
    add constraint game_title_th_not_blank
        check (title_th is null or length(btrim(title_th)) > 0);
-- [jooq ignore stop]

-- The inventory searches and orders on both titles now, so both carry a folded
-- index. As in V3, the simulator has no expression indexes and generates
-- nothing from an index anyway, so it skips these too.
-- [jooq ignore start]
drop index game_title_folded_idx;

create index game_title_en_folded_idx on game (lower(title_en));

create index game_title_th_folded_idx on game (lower(title_th));
-- [jooq ignore stop]
