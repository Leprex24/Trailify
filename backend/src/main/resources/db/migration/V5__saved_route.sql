-- V5: zapisane trasy (gwiazdka): użytkownik zapamiętuje publiczne trasy innych osób.

set local search_path to public, extensions;

create table saved_route (
    user_id    uuid        not null references profile (id) on delete cascade,
    route_id   bigint      not null references route (id) on delete cascade,
    created_at timestamptz not null default now(),
    primary key (user_id, route_id)
);

create index saved_route_route_idx on saved_route (route_id);

alter table saved_route enable row level security;
