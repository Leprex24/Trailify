-- V3: ciekawe punkty (POI), zebrane punkty, recenzje i zdjęcia.

set local search_path to public, extensions;

create table point_of_interest (
    id               bigint generated always as identity primary key,
    -- po usunięciu konta zatwierdzony punkt zostaje, ale anonimowo (null);
    -- zgłoszenia oczekujące i odrzucone backend usuwa razem z kontem
    submitted_by     uuid         references profile (id) on delete set null,
    name             varchar(255) not null,
    description      text,
    latitude         double precision not null check (latitude between -90 and 90),
    longitude        double precision not null check (longitude between -180 and 180),
    status           varchar(20)  not null default 'PENDING'
                     check (status in ('PENDING', 'APPROVED', 'REJECTED')),
    rejection_reason text,
    created_at       timestamptz  not null default now(),
    -- wyliczane z latitude/longitude, tylko do odczytu (zapytania po obszarze)
    location         geometry(Point, 4326)
                     generated always as (st_setsrid(st_makepoint(longitude, latitude), 4326)) stored
);

create index poi_location_idx on point_of_interest using gist (location);
create index poi_status_idx on point_of_interest (status);
create index poi_submitted_by_idx on point_of_interest (submitted_by);

alter table point_of_interest enable row level security;

-- Zebranie punktu przez użytkownika (relacja wiele-do-wielu).
create table poi_collection (
    id           bigint generated always as identity primary key,
    user_id      uuid        not null references profile (id) on delete cascade,
    poi_id       bigint      not null references point_of_interest (id) on delete cascade,
    collected_at timestamptz not null default now(),
    constraint poi_collection_unique unique (user_id, poi_id)
);

create index poi_collection_poi_idx on poi_collection (poi_id);

alter table poi_collection enable row level security;

create table review (
    id         bigint generated always as identity primary key,
    user_id    uuid        not null references profile (id) on delete cascade,
    route_id   bigint      not null references route (id) on delete cascade,
    rating     smallint    not null check (rating between 1 and 5),
    comment    text,
    created_at timestamptz not null default now(),
    -- jedna recenzja użytkownika na trasę
    constraint review_unique unique (user_id, route_id)
);

create index review_route_idx on review (route_id);

alter table review enable row level security;

-- Zdjęcie należy dokładnie do jednego obiektu: recenzji, trasy albo punktu POI.
-- Sam plik leży w Supabase Storage, tu są tylko metadane.
create table photo (
    id          bigint generated always as identity primary key,
    review_id   bigint references review (id) on delete cascade,
    route_id    bigint references route (id) on delete cascade,
    poi_id      bigint references point_of_interest (id) on delete cascade,
    url         varchar(1024) not null,
    storage_key varchar(512)  not null unique,
    uploaded_at timestamptz   not null default now(),
    constraint photo_single_parent check (num_nonnulls(review_id, route_id, poi_id) = 1)
);

create index photo_review_idx on photo (review_id) where review_id is not null;
create index photo_route_idx on photo (route_id) where route_id is not null;
-- punkt POI ma najwyżej jedno zdjęcie
create unique index photo_poi_unique on photo (poi_id) where poi_id is not null;

alter table photo enable row level security;
