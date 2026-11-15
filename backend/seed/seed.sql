-- =====================================================================
-- Trailify: dane testowe (tydzień 8) - trasy i ciekawe punkty w Lublinie i okolicy.
-- Plik wygenerowany; współrzędne są przybliżone (do testów i prezentacji).
--
-- Uruchomienie: Supabase -> SQL Editor -> wklej całość -> Run (albo psql na bazie z migracjami V1-V4).
-- Skrypt można uruchamiać wielokrotnie: najpierw usuwa dane z poprzedniego uruchomienia (po nazwach).
--
-- Właściciel tras: pierwszy administrator, a gdy go nie ma - najstarsze konto.
-- Musi istnieć co najmniej jedno konto (zarejestruj się w aplikacji przed uruchomieniem).
-- Punkty POI nie mają zgłaszającego (jak dodane przez administratora).
-- =====================================================================

-- funkcje PostGIS leżą w schemacie extensions (Supabase)
set search_path to public, extensions;

do $$
declare
    owner uuid;
    new_id bigint;
begin
    select id into owner from profile order by (role = 'ADMIN') desc, created_at limit 1;
    if owner is null then
        raise exception 'Brak kont w tabeli profile: najpierw zarejestruj się w aplikacji';
    end if;

    -- poprzednie uruchomienie (zdjęcia, recenzje i kolekcje znikają kaskadowo)
    delete from route where user_id = owner and name in ('Pętla wokół Zalewu Zemborzyckiego', 'Stare Miasto i Zamek Lubelski', 'Bulwary nad Bystrzycą', 'Górki Czechowskie', 'Lublin - Nałęczów', 'Ogród Saski i Aleje Racławickie', 'Do Ogrodu Botanicznego UMCS', 'Rowerem do Skansenu', 'Rezerwat Stary Gaj', 'Kazimierz Dolny - wąwozy lessowe');
    delete from point_of_interest where submitted_by is null and name in ('Brama Krakowska', 'Zamek Lubelski', 'Wieża Trynitarska', 'Brama Grodzka', 'Lubelska Trasa Podziemna', 'Plac Litewski', 'Ogród Saski', 'Centrum Spotkania Kultur', 'Ogród Botaniczny UMCS', 'Muzeum Wsi Lubelskiej', 'Plaża nad Zalewem Zemborzyckim', 'Rezerwat Stary Gaj', 'Górki Czechowskie - punkt widokowy', 'Mural na Zielonej', 'Punkt widokowy nad Bystrzycą');
    delete from photo where storage_key like 'seed/%';

    -- 1. Pętla wokół Zalewu Zemborzyckiego: ok. 7.7 km
    insert into route (user_id, name, description, activity_type, is_tourist, is_scenic, is_public,
                       total_distance, duration_seconds, geometry, created_at)
    select owner, 'Pętla wokół Zalewu Zemborzyckiego', 'Asfaltowa ścieżka rowerowa dookoła zalewu. Płasko, dużo widoków na wodę, po drodze plaże i przystań.', 'CYCLING', true, true, true,
           ST_Length(g::geography), 1534, g, now() - interval '9 days'
    from ST_SetSRID(ST_GeomFromText('LINESTRING(22.612 51.219, 22.62 51.215, 22.629 51.2085, 22.629 51.201, 22.62 51.196, 22.608 51.195, 22.598 51.199, 22.596 51.206, 22.601 51.213, 22.612 51.219)'), 4326) as g
    returning id into new_id;
    insert into photo (route_id, url, storage_key) values (new_id, 'https://picsum.photos/seed/trailify-route-1-1/800/600', 'seed/route-1-1.jpg');
    insert into photo (route_id, url, storage_key) values (new_id, 'https://picsum.photos/seed/trailify-route-1-2/800/600', 'seed/route-1-2.jpg');
    insert into photo (route_id, url, storage_key) values (new_id, 'https://picsum.photos/seed/trailify-route-1-3/800/600', 'seed/route-1-3.jpg');

    -- 2. Stare Miasto i Zamek Lubelski: ok. 1.0 km
    insert into route (user_id, name, description, activity_type, is_tourist, is_scenic, is_public,
                       total_distance, duration_seconds, geometry, created_at)
    select owner, 'Stare Miasto i Zamek Lubelski', 'Krótki spacer od Placu Litewskiego przez Bramę Krakowską i Rynek do Zamku.', 'WALKING', true, false, true,
           ST_Length(g::geography), 761, g, now() - interval '8 days'
    from ST_SetSRID(ST_GeomFromText('LINESTRING(22.5617 51.2484, 22.5635 51.2482, 22.565 51.2481, 22.5675 51.2477, 22.5688 51.2479, 22.5697 51.2486, 22.5705 51.2493, 22.5722 51.2502, 22.5735 51.2505)'), 4326) as g
    returning id into new_id;
    insert into photo (route_id, url, storage_key) values (new_id, 'https://picsum.photos/seed/trailify-route-2-1/800/600', 'seed/route-2-1.jpg');
    insert into photo (route_id, url, storage_key) values (new_id, 'https://picsum.photos/seed/trailify-route-2-2/800/600', 'seed/route-2-2.jpg');

    -- 3. Bulwary nad Bystrzycą: ok. 3.1 km
    insert into route (user_id, name, description, activity_type, is_tourist, is_scenic, is_public,
                       total_distance, duration_seconds, geometry, created_at)
    select owner, 'Bulwary nad Bystrzycą', 'Bieg wzdłuż rzeki od Dąbrowy do Podzamcza. Równa nawierzchnia, dobra na tempo.', 'RUNNING', false, true, true,
           ST_Length(g::geography), 1129, g, now() - interval '7 days'
    from ST_SetSRID(ST_GeomFromText('LINESTRING(22.5395 51.2397, 22.548 51.241, 22.556 51.2425, 22.564 51.244, 22.572 51.2455, 22.58 51.2468, 22.583 51.247)'), 4326) as g
    returning id into new_id;
    insert into photo (route_id, url, storage_key) values (new_id, 'https://picsum.photos/seed/trailify-route-3-1/800/600', 'seed/route-3-1.jpg');

    -- 4. Górki Czechowskie: ok. 2.7 km
    insert into route (user_id, name, description, activity_type, is_tourist, is_scenic, is_public,
                       total_distance, duration_seconds, geometry, created_at)
    select owner, 'Górki Czechowskie', 'Pagórkowata pętla po terenie dawnego poligonu. Ścieżki ziemne, kilka mocnych podbiegów.', 'RUNNING', false, true, true,
           ST_Length(g::geography), 986, g, now() - interval '6 days'
    from ST_SetSRID(ST_GeomFromText('LINESTRING(22.547 51.269, 22.544 51.2705, 22.542 51.272, 22.538 51.275, 22.543 51.277, 22.548 51.276, 22.55 51.2745, 22.552 51.271, 22.547 51.269)'), 4326) as g
    returning id into new_id;
    insert into photo (route_id, url, storage_key) values (new_id, 'https://picsum.photos/seed/trailify-route-4-1/800/600', 'seed/route-4-1.jpg');
    insert into photo (route_id, url, storage_key) values (new_id, 'https://picsum.photos/seed/trailify-route-4-2/800/600', 'seed/route-4-2.jpg');

    -- 5. Lublin - Nałęczów: ok. 22.0 km
    insert into route (user_id, name, description, activity_type, is_tourist, is_scenic, is_public,
                       total_distance, duration_seconds, geometry, created_at)
    select owner, 'Lublin - Nałęczów', 'Dłuższa wycieczka rowerowa do uzdrowiska. Większość trasy po drogach o małym ruchu.', 'CYCLING', true, false, true,
           ST_Length(g::geography), 4407, g, now() - interval '5 days'
    from ST_SetSRID(ST_GeomFromText('LINESTRING(22.525 51.247, 22.49 51.255, 22.45 51.263, 22.4 51.27, 22.35 51.278, 22.29 51.283, 22.216 51.286)'), 4326) as g
    returning id into new_id;
    insert into photo (route_id, url, storage_key) values (new_id, 'https://picsum.photos/seed/trailify-route-5-1/800/600', 'seed/route-5-1.jpg');

    -- 6. Ogród Saski i Aleje Racławickie: ok. 1.2 km
    insert into route (user_id, name, description, activity_type, is_tourist, is_scenic, is_public,
                       total_distance, duration_seconds, geometry, created_at)
    select owner, 'Ogród Saski i Aleje Racławickie', 'Spokojny spacer przez najstarszy park w mieście.', 'WALKING', false, true, true,
           ST_Length(g::geography), 990, g, now() - interval '4 days'
    from ST_SetSRID(ST_GeomFromText('LINESTRING(22.5585 51.2492, 22.557 51.2505, 22.556 51.2518, 22.5545 51.253, 22.5525 51.2525, 22.5535 51.2512, 22.5555 51.25, 22.5585 51.2492)'), 4326) as g
    returning id into new_id;
    insert into photo (route_id, url, storage_key) values (new_id, 'https://picsum.photos/seed/trailify-route-6-1/800/600', 'seed/route-6-1.jpg');

    -- 7. Do Ogrodu Botanicznego UMCS: ok. 2.7 km
    insert into route (user_id, name, description, activity_type, is_tourist, is_scenic, is_public,
                       total_distance, duration_seconds, geometry, created_at)
    select owner, 'Do Ogrodu Botanicznego UMCS', 'Spacer z kampusu UMCS do ogrodu botanicznego wzdłuż Sławinkowskiej.', 'WALKING', true, true, true,
           ST_Length(g::geography), 2127, g, now() - interval '3 days'
    from ST_SetSRID(ST_GeomFromText('LINESTRING(22.543 51.2465, 22.538 51.248, 22.532 51.25, 22.526 51.253, 22.52 51.256, 22.516 51.259, 22.5145 51.2615)'), 4326) as g
    returning id into new_id;
    insert into photo (route_id, url, storage_key) values (new_id, 'https://picsum.photos/seed/trailify-route-7-1/800/600', 'seed/route-7-1.jpg');

    -- 8. Rowerem do Skansenu: ok. 5.0 km
    insert into route (user_id, name, description, activity_type, is_tourist, is_scenic, is_public,
                       total_distance, duration_seconds, geometry, created_at)
    select owner, 'Rowerem do Skansenu', 'Trasa z centrum do Muzeum Wsi Lubelskiej, częściowo ścieżką rowerową.', 'CYCLING', true, false, true,
           ST_Length(g::geography), 998, g, now() - interval '2 days'
    from ST_SetSRID(ST_GeomFromText('LINESTRING(22.56 51.248, 22.55 51.25, 22.54 51.252, 22.52 51.257, 22.508 51.26, 22.5 51.262, 22.493 51.264)'), 4326) as g
    returning id into new_id;

    -- 9. Rezerwat Stary Gaj: ok. 3.0 km
    insert into route (user_id, name, description, activity_type, is_tourist, is_scenic, is_public,
                       total_distance, duration_seconds, geometry, created_at)
    select owner, 'Rezerwat Stary Gaj', 'Leśna pętla biegowa w rezerwacie. Cień nawet w upał, miękka nawierzchnia.', 'RUNNING', false, true, true,
           ST_Length(g::geography), 1077, g, now() - interval '1 days'
    from ST_SetSRID(ST_GeomFromText('LINESTRING(22.518 51.209, 22.512 51.206, 22.515 51.203, 22.522 51.201, 22.528 51.204, 22.526 51.2075, 22.518 51.209)'), 4326) as g
    returning id into new_id;

    -- 10. Kazimierz Dolny - wąwozy lessowe: ok. 2.2 km
    insert into route (user_id, name, description, activity_type, is_tourist, is_scenic, is_public,
                       total_distance, duration_seconds, geometry, created_at)
    select owner, 'Kazimierz Dolny - wąwozy lessowe', 'Spacer po Rynku i wąwozach. Strome podejścia, piękne widoki na Wisłę.', 'WALKING', true, true, true,
           ST_Length(g::geography), 1773, g, now() - interval '0 days'
    from ST_SetSRID(ST_GeomFromText('LINESTRING(21.948 51.321, 21.9505 51.322, 21.953 51.323, 21.956 51.326, 21.95 51.327, 21.9465 51.3255, 21.944 51.3245, 21.948 51.321)'), 4326) as g
    returning id into new_id;
    insert into photo (route_id, url, storage_key) values (new_id, 'https://picsum.photos/seed/trailify-route-10-1/800/600', 'seed/route-10-1.jpg');
    insert into photo (route_id, url, storage_key) values (new_id, 'https://picsum.photos/seed/trailify-route-10-2/800/600', 'seed/route-10-2.jpg');

    -- ciekawe punkty
    insert into point_of_interest (submitted_by, name, description, latitude, longitude, status)
    values (null, 'Brama Krakowska', 'Gotycka brama miejska z XIV wieku, symbol Lublina.', 51.2477, 22.5675, 'APPROVED');
    insert into point_of_interest (submitted_by, name, description, latitude, longitude, status)
    values (null, 'Zamek Lubelski', 'Zamek z kaplicą Trójcy Świętej z bizantyjsko-ruskimi freskami.', 51.2502, 22.5722, 'APPROVED');
    insert into point_of_interest (submitted_by, name, description, latitude, longitude, status)
    values (null, 'Wieża Trynitarska', 'Punkt widokowy na Stare Miasto, ponad 40 m wysokości.', 51.247, 22.5665, 'APPROVED');
    insert into point_of_interest (submitted_by, name, description, latitude, longitude, status)
    values (null, 'Brama Grodzka', 'Brama łącząca Stare Miasto z zamkiem, dawniej granica dzielnicy żydowskiej.', 51.2493, 22.5705, 'APPROVED');
    insert into point_of_interest (submitted_by, name, description, latitude, longitude, status)
    values (null, 'Lubelska Trasa Podziemna', 'Trasa przez połączone piwnice kamienic pod Rynkiem.', 51.2478, 22.5686, 'APPROVED');
    insert into point_of_interest (submitted_by, name, description, latitude, longitude, status)
    values (null, 'Plac Litewski', 'Główny plac miasta z fontanną multimedialną.', 51.2484, 22.5617, 'APPROVED');
    insert into point_of_interest (submitted_by, name, description, latitude, longitude, status)
    values (null, 'Ogród Saski', 'Najstarszy park publiczny w Lublinie, otwarty w 1837 roku.', 51.2525, 22.5555, 'APPROVED');
    insert into point_of_interest (submitted_by, name, description, latitude, longitude, status)
    values (null, 'Centrum Spotkania Kultur', 'Nowoczesne centrum kultury z ogrodem na dachu.', 51.247, 22.556, 'APPROVED');
    insert into point_of_interest (submitted_by, name, description, latitude, longitude, status)
    values (null, 'Ogród Botaniczny UMCS', 'Ogród z kolekcją ponad 6 tysięcy gatunków roślin.', 51.2615, 22.5145, 'APPROVED');
    insert into point_of_interest (submitted_by, name, description, latitude, longitude, status)
    values (null, 'Muzeum Wsi Lubelskiej', 'Skansen z dawną zabudową wsi i miasteczka.', 51.264, 22.493, 'APPROVED');
    insert into point_of_interest (submitted_by, name, description, latitude, longitude, status)
    values (null, 'Plaża nad Zalewem Zemborzyckim', 'Kąpielisko i przystań przy ścieżce rowerowej.', 51.216, 22.6175, 'APPROVED');
    insert into point_of_interest (submitted_by, name, description, latitude, longitude, status)
    values (null, 'Rezerwat Stary Gaj', 'Rezerwat leśny z grądem i buczyną.', 51.205, 22.518, 'APPROVED');
    insert into point_of_interest (submitted_by, name, description, latitude, longitude, status)
    values (null, 'Górki Czechowskie - punkt widokowy', 'Widok na panoramę północnej części miasta.', 51.2745, 22.542, 'APPROVED');
    insert into point_of_interest (submitted_by, name, description, latitude, longitude, status)
    values (null, 'Mural na Zielonej', 'Duży mural na ścianie kamienicy (zgłoszenie do sprawdzenia).', 51.2468, 22.5598, 'PENDING');
    insert into point_of_interest (submitted_by, name, description, latitude, longitude, status)
    values (null, 'Punkt widokowy nad Bystrzycą', 'Kładka z widokiem na rzekę (zgłoszenie do sprawdzenia).', 51.244, 22.564, 'PENDING');

    raise notice 'Dodano 10 tras i 15 punktów (właściciel tras: %)', owner;
end $$;
