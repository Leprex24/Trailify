-- V6: obserwowanie użytkowników, prywatność profilu i zdjęcie w tle profilu.
--
-- Profil prywatny: inni widzą tylko nazwę, avatar i tło, a statystyki, listy obserwujących
-- i obserwowanych, publiczne trasy oraz zebrane punkty są ukryte (patrz SocialRules).
-- Stare migracje (V1-V5) zostają bez zmian.

alter table profile
    add column is_private     boolean not null default false,
    add column background_url varchar(1024);

create table follow (
    follower_id uuid        not null references profile (id) on delete cascade,
    followee_id uuid        not null references profile (id) on delete cascade,
    created_at  timestamptz not null default now(),
    primary key (follower_id, followee_id),
    -- samego siebie obserwować nie można
    constraint follow_not_self check (follower_id <> followee_id)
);

-- lista "obserwujący" (kto obserwuje X) i "obserwowani" (kogo obserwuje X), od najnowszych
create index follow_followee_idx on follow (followee_id, created_at desc);
create index follow_follower_idx on follow (follower_id, created_at desc);

-- jak w pozostałych tabelach: RLS bez polityk, dostęp tylko przez backend
alter table follow enable row level security;
