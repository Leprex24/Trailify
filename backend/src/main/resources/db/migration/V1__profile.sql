-- V1: PostGIS oraz profil użytkownika (zastępuje tabelę User z ERD).
--
-- Hasła obsługuje Supabase Auth, dlatego nie ma tu passwordHash.
-- Identyfikator profilu to ten sam UUID co w auth.users, a profil tworzy się
-- automatycznie triggerem przy rejestracji (email lub Google).

set local search_path to public, extensions;

create extension if not exists postgis;

create schema if not exists private;

create table profile (
    id           uuid primary key references auth.users (id) on delete cascade,
    email        varchar(255) not null unique,
    display_name varchar(255) not null,
    avatar_url   varchar(1024),
    role         varchar(20)  not null default 'USER'
                 check (role in ('USER', 'ADMIN')),
    status       varchar(20)  not null default 'ACTIVE'
                 check (status in ('ACTIVE', 'BLOCKED')),
    created_at   timestamptz  not null default now()
);

-- RLS włączone bez żadnych polityk: przez publiczne API Supabase nikt nie ma
-- dostępu do tabeli. Backend łączy się jako właściciel tabel (rola postgres),
-- który omija RLS.
alter table profile enable row level security;

-- Nowe konto w Supabase Auth -> nowy wiersz w profile.
create function private.handle_new_user()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
begin
    insert into public.profile (id, email, display_name)
    values (
        new.id,
        new.email,
        coalesce(
            nullif(trim(new.raw_user_meta_data ->> 'display_name'), ''),
            nullif(trim(new.raw_user_meta_data ->> 'full_name'), ''),
            nullif(trim(new.raw_user_meta_data ->> 'name'), ''),
            split_part(new.email, '@', 1)
        )
    );
    return new;
end;
$$;

create trigger on_auth_user_created
    after insert on auth.users
    for each row execute function private.handle_new_user();

-- Zmiana adresu e-mail w Supabase Auth -> aktualizacja kopii w profile.
create function private.handle_user_email_change()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
begin
    update public.profile set email = new.email where id = new.id;
    return new;
end;
$$;

create trigger on_auth_user_email_changed
    after update of email on auth.users
    for each row
    when (old.email is distinct from new.email)
    execute function private.handle_user_email_change();

-- Konta, które powstały zanim ta migracja została uruchomiona.
insert into profile (id, email, display_name)
select u.id,
       u.email,
       coalesce(
           nullif(trim(u.raw_user_meta_data ->> 'display_name'), ''),
           nullif(trim(u.raw_user_meta_data ->> 'full_name'), ''),
           nullif(trim(u.raw_user_meta_data ->> 'name'), ''),
           split_part(u.email, '@', 1)
       )
from auth.users u
on conflict (id) do nothing;
