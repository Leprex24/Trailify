-- V2: trasy, aktywności i punkty GPS.

set local search_path to public, extensions;

-- Trasa udostępniona (lub zapisana prywatnie) przez użytkownika.
create table route (
    id               bigint generated always as identity primary key,
    user_id          uuid         not null references profile (id) on delete cascade,
    name             varchar(255) not null,
    description      text,
    activity_type    varchar(20)  not null
                     check (activity_type in ('CYCLING', 'RUNNING', 'WALKING')),
    is_tourist       boolean      not null default false,
    is_scenic        boolean      not null default false,
    -- domyślnie prywatna; publikacja wymaga świadomego ustawienia true
    is_public        boolean      not null default false,
    total_distance   double precision check (total_distance >= 0),  -- metry
    duration_seconds integer check (duration_seconds >= 0),
    geometry         geometry(LineString, 4326) not null,
    created_at       timestamptz  not null default now()
);

create index route_user_idx on route (user_id);
-- zapytania po obszarze mapy (bbox)
create index route_geometry_idx on route using gist (geometry);
-- zapytania po promieniu w metrach: ST_DWithin(geometry::geography, ..., metry)
create index route_geography_idx on route using gist ((geometry::geography));
-- lista tras publicznych od najnowszej
create index route_public_created_idx on route (created_at desc) where is_public;

alter table route enable row level security;

-- Pojedyncze wyjście użytkownika w teren.
create table activity (
    id               bigint generated always as identity primary key,
    -- UUID generowany na telefonie; chroni przed duplikatem przy ponownej wysyłce
    client_id        uuid        not null,
    user_id          uuid        not null references profile (id) on delete cascade,
    -- po usunięciu trasy aktywność zostaje (route_id = null)
    route_id         bigint      references route (id) on delete set null,
    type             varchar(20) not null
                     check (type in ('CYCLING', 'RUNNING', 'WALKING')),
    start_time       timestamptz not null,
    end_time         timestamptz,
    distance         double precision check (distance >= 0),         -- metry
    duration_seconds integer check (duration_seconds >= 0),
    avg_pace         double precision check (avg_pace >= 0),         -- min/km
    step_count       integer check (step_count >= 0),
    geometry         geometry(LineString, 4326) not null,
    created_at       timestamptz not null default now(),
    constraint activity_client_unique unique (user_id, client_id),
    constraint activity_time_order check (end_time is null or end_time >= start_time)
);

create index activity_user_start_idx on activity (user_id, start_time desc);
-- sprawdzenie "użytkownik ukończył trasę" (warunek dodania recenzji)
create index activity_user_route_idx on activity (user_id, route_id)
    where route_id is not null;
create index activity_route_idx on activity (route_id) where route_id is not null;

alter table activity enable row level security;

-- Pojedynczy punkt śladu GPS.
create table gps_point (
    id              bigint generated always as identity primary key,
    activity_id     bigint           not null references activity (id) on delete cascade,
    latitude        double precision not null check (latitude between -90 and 90),
    longitude       double precision not null check (longitude between -180 and 180),
    altitude        double precision,                                -- m n.p.m.
    speed           double precision check (speed >= 0),             -- m/s
    recorded_at     timestamptz      not null,
    -- true = punkt odtworzony przez AI
    is_reconstructed boolean         not null default false
);

create index gps_point_activity_idx on gps_point (activity_id, recorded_at);

alter table gps_point enable row level security;
