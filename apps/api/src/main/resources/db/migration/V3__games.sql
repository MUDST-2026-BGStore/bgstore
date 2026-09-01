-- Game catalogue and per-branch stock, plus the branch directory it needs.

create table branch (
    id uuid primary key,
    name text not null unique,
    created_at timestamptz not null default now()
);

-- The stores BGStore operates. Reference data rather than sample data: the
-- game screens cannot record copies without a branch to record them against.
insert into branch (id, name)
values ('3f0d7d5a-9a2b-4a71-8f0e-000000000001', 'Central Rama II'),
       ('3f0d7d5a-9a2b-4a71-8f0e-000000000002', 'Big C Rama I'),
       ('3f0d7d5a-9a2b-4a71-8f0e-000000000003', 'Big C Rama IX'),
       ('3f0d7d5a-9a2b-4a71-8f0e-000000000004', 'Sukhumvit'),
       ('3f0d7d5a-9a2b-4a71-8f0e-000000000005', 'Silom'),
       ('3f0d7d5a-9a2b-4a71-8f0e-000000000006', 'Thonglor');

create table game (
    id uuid primary key default gen_random_uuid(),
    title text not null,
    description text,
    category text not null,
    min_players integer not null,
    max_players integer not null,
    play_time_minutes integer,
    difficulty text,
    tags text[] not null default '{}',
    lifecycle text not null default 'active',
    -- Owned by the play-session module once it exists; null until a session
    -- has used this game.
    last_played_at timestamptz,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint game_category_known check (category in ('family', 'card', 'party', 'strategy')),
    constraint game_lifecycle_known check (lifecycle in ('active', 'retired')),
    constraint game_title_not_blank check (length(btrim(title)) between 1 and 120),
    constraint game_description_length check (description is null or length(description) <= 160),
    constraint game_difficulty_length check (difficulty is null or length(difficulty) <= 60),
    constraint game_min_players_range check (min_players between 1 and 99),
    constraint game_max_players_range check (max_players between 1 and 99),
    constraint game_player_range check (max_players >= min_players),
    constraint game_play_time_range check (play_time_minutes is null or play_time_minutes between 1 and 600)
);

-- The inventory list searches on a case-insensitive title match and orders by
-- title, so the index carries the folded value.
create index game_title_folded_idx on game (lower(title));

create table game_branch_stock (
    game_id uuid not null references game (id) on delete cascade,
    branch_id uuid not null references branch (id),
    copies integer not null,
    -- Maintained by the play-session module when copies are assigned to a
    -- session; availability is copies minus this.
    copies_in_use integer not null default 0,
    primary key (game_id, branch_id),
    constraint game_branch_stock_copies_range check (copies between 0 and 999),
    constraint game_branch_stock_in_use_range check (copies_in_use between 0 and copies)
);

create index game_branch_stock_branch_idx on game_branch_stock (branch_id);
