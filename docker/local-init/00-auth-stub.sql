-- Tylko do lokalnego Dockera (nie jest migracją Flyway i nie trafia na Supabase).
-- Supabase ma gotowy schemat auth z tabelą users. Lokalny PostgreSQL go nie ma,
-- więc tworzymy minimalną wersję, żeby migracje (klucz obcy do auth.users i triggery)
-- mogły się wykonać tak samo jak na Supabase.
create schema if not exists auth;

create table if not exists auth.users (
    id                 uuid primary key default gen_random_uuid(),
    email              text unique,
    raw_user_meta_data jsonb not null default '{}'::jsonb,
    created_at         timestamptz not null default now()
);
