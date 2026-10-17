-- V4: statystyki ocen tras i ranking rekomendacji.
--
-- recommendation_score to średnia bayesowska, która uwzględnia liczbę ocen:
--   (v * R + m * C) / (v + m)
--   v = liczba ocen trasy, R = jej średnia ocena,
--   C = średnia wszystkich ocen w systemie (3.5, gdy nie ma jeszcze żadnych),
--   m = waga a priori (tu 5, czyli trasa potrzebuje kilku ocen, żeby wyprzedzić średnią).
-- Dzięki temu trasa z jedną oceną 5.0 nie wyprzedza trasy z 40 ocenami 4.7.
-- Wzór i wartość m można zmienić w kolejnej migracji bez ruszania reszty kodu.

set local search_path to public, extensions;

create view route_rating_stats
with (security_invoker = true) as
with global_avg as (
    select coalesce(avg(rating), 3.5) as c from review
)
select r.id                                         as route_id,
       count(rv.id)::int                            as review_count,
       avg(rv.rating)::double precision             as average_rating,
       ((count(rv.id) * coalesce(avg(rv.rating), 0) + 5 * g.c)
            / (count(rv.id) + 5))::double precision as recommendation_score
from route r
cross join global_avg g
left join review rv on rv.route_id = r.id
group by r.id, g.c;
