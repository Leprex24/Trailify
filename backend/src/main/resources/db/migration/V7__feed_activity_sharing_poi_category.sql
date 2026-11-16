-- V7: oś czasu osób obserwowanych (wpisy, polubienia, komentarze), udostępnianie aktywności
-- i kategorie ciekawych punktów. Stare migracje (V1-V6) zostają bez zmian.

-- Aktywność jest domyślnie prywatna: widzi ją tylko właściciel. Po udostępnieniu widać ją
-- na profilu właściciela i na osi czasu jego obserwujących.
alter table activity
    add column is_shared boolean not null default false;

create index activity_shared_idx on activity (user_id, start_time desc) where is_shared;

-- Kategoria widoczna tylko na karcie punktu (na mapie wszystkie punkty wyglądają tak samo).
alter table point_of_interest
    add column category varchar(20) not null default 'OTHER'
        check (category in ('VIEWPOINT', 'NATURE', 'HISTORIC', 'CULTURE', 'RECREATION', 'OTHER'));

-- Wpis na osi czasu powstaje w chwili akcji i dotyczy dokładnie jednego obiektu:
-- opublikowanej trasy, udostępnionej aktywności, oceny albo zebranego punktu.
-- Usunięcie obiektu usuwa wpis razem z polubieniami i komentarzami (on delete cascade).
-- Widoczność (trasa publiczna, aktywność udostępniona, profil nie jest prywatny) sprawdza
-- zapytanie w momencie odczytu, więc wpis nie wymaga sprzątania po zmianie ustawień.
create table feed_event (
    id            bigint generated always as identity primary key,
    user_id       uuid        not null references profile (id) on delete cascade,
    type          varchar(20) not null
                  check (type in ('ROUTE_PUBLISHED', 'ACTIVITY', 'REVIEW', 'POI_COLLECTED')),
    route_id      bigint references route (id) on delete cascade,
    activity_id   bigint references activity (id) on delete cascade,
    review_id     bigint references review (id) on delete cascade,
    collection_id bigint references poi_collection (id) on delete cascade,
    created_at    timestamptz not null default now(),
    constraint feed_event_single_parent check (num_nonnulls(route_id, activity_id, review_id, collection_id) = 1),
    constraint feed_event_type_parent check (
        (type = 'ROUTE_PUBLISHED' and route_id is not null)
        or (type = 'ACTIVITY' and activity_id is not null)
        or (type = 'REVIEW' and review_id is not null)
        or (type = 'POI_COLLECTED' and collection_id is not null)
    )
);

-- jeden wpis na obiekt (ponowna publikacja albo udostępnienie nie dubluje wpisu)
create unique index feed_event_route_unique on feed_event (route_id) where route_id is not null;
create unique index feed_event_activity_unique on feed_event (activity_id) where activity_id is not null;
create unique index feed_event_review_unique on feed_event (review_id) where review_id is not null;
create unique index feed_event_collection_unique on feed_event (collection_id) where collection_id is not null;

-- oś czasu: wpisy wybranych autorów od najnowszych
create index feed_event_user_created_idx on feed_event (user_id, created_at desc, id desc);

alter table feed_event enable row level security;

create table feed_like (
    event_id   bigint      not null references feed_event (id) on delete cascade,
    user_id    uuid        not null references profile (id) on delete cascade,
    created_at timestamptz not null default now(),
    primary key (event_id, user_id)
);

alter table feed_like enable row level security;

create table feed_comment (
    id               bigint generated always as identity primary key,
    event_id         bigint      not null references feed_event (id) on delete cascade,
    user_id          uuid        not null references profile (id) on delete cascade,
    -- komu odpowiada komentarz (oznaczenie); po usunięciu konta oznaczenie znika, komentarz zostaje
    reply_to_user_id uuid        references profile (id) on delete set null,
    body             varchar(1000) not null check (length(btrim(body)) > 0),
    created_at       timestamptz not null default now()
);

create index feed_comment_event_idx on feed_comment (event_id, created_at, id);

alter table feed_comment enable row level security;

-- Wpisy z dotychczasowych danych, żeby oś czasu nie zaczynała od pustej: publiczne trasy,
-- oceny i zebrane punkty. Aktywności są prywatne, więc nie mają wpisów.
insert into feed_event (user_id, type, route_id, created_at)
select user_id, 'ROUTE_PUBLISHED', id, created_at from route where is_public;

insert into feed_event (user_id, type, review_id, created_at)
select user_id, 'REVIEW', id, created_at from review;

insert into feed_event (user_id, type, collection_id, created_at)
select user_id, 'POI_COLLECTED', id, collected_at from poi_collection;
