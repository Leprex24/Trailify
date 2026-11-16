-- =====================================================================
-- Trailify: dane testowe społeczności (oś czasu, obserwowanie, opinie, aktywności, zebrane punkty).
-- Plik wygenerowany przez generate_seed_social.py.
--
-- Wymagania: migracje V1-V7 i wcześniej uruchomiony seed.sql (punkty POI w Lublinie).
-- Uruchomienie: Supabase -> SQL Editor -> wklej całość -> Run.
-- Skrypt można uruchamiać wielokrotnie: najpierw usuwa dane z poprzedniego uruchomienia.
--
-- Konto główne ("ja"): ustaw nazwę wyświetlaną poniżej (me_name). Gdy zostawisz null, wybierany jest
-- pierwszy administrator, a gdy go nie ma - najstarsze konto.
-- Konta testowe mają adresy seed.<imię>@trailify.test i nie mają hasła (nie da się na nie zalogować).
-- =====================================================================

set search_path to public, extensions;

do $$
declare
    me_name text := null;   -- np. 'Kacper' (wielkość liter nie ma znaczenia)
    me uuid;
    n int;
begin
    if me_name is not null then
        select id into me from profile where lower(display_name) = lower(me_name)
            and email not like 'seed.%@trailify.test' order by created_at limit 1;
        if me is null then
            raise exception 'Nie ma konta o nazwie %: sprawdź me_name', me_name;
        end if;
    else
        select id into me from profile where email not like 'seed.%@trailify.test'
            order by (role = 'ADMIN') desc, created_at limit 1;
        if me is null then
            raise exception 'Brak kont w tabeli profile: najpierw zarejestruj się w aplikacji';
        end if;
    end if;

    select count(*) into n from point_of_interest where status = 'APPROVED' and name in ('Brama Grodzka', 'Brama Krakowska', 'Górki Czechowskie - punkt widokowy', 'Lubelska Trasa Podziemna', 'Muzeum Wsi Lubelskiej', 'Ogród Botaniczny UMCS', 'Ogród Saski', 'Plac Litewski', 'Plaża nad Zalewem Zemborzyckim', 'Rezerwat Stary Gaj', 'Wieża Trynitarska', 'Zamek Lubelski');
    if n < 12 then
        raise exception 'Brakuje punktów POI z seed.sql (znaleziono % z 12): najpierw uruchom seed.sql', n;
    end if;

    -- poprzednie uruchomienie: konta testowe znikają razem ze wszystkim, co z nimi związane (kaskadowo)
    delete from activity where user_id = me and client_id in ('5eed0013-0000-4000-8000-00000000c11d', '5eed0014-0000-4000-8000-00000000c11d', '5eed0015-0000-4000-8000-00000000c11d', '5eed0019-0000-4000-8000-00000000c11d');
    delete from poi_collection where user_id = me and poi_id in (select id from point_of_interest where name in ('Brama Krakowska', 'Zamek Lubelski'));
    delete from auth.users where email like 'seed.%@trailify.test';

    -- kategorie punktów (domyślnie OTHER)
    update point_of_interest set category = 'HISTORIC' where name = 'Brama Krakowska';
    update point_of_interest set category = 'HISTORIC' where name = 'Zamek Lubelski';
    update point_of_interest set category = 'VIEWPOINT' where name = 'Wieża Trynitarska';
    update point_of_interest set category = 'HISTORIC' where name = 'Brama Grodzka';
    update point_of_interest set category = 'CULTURE' where name = 'Lubelska Trasa Podziemna';
    update point_of_interest set category = 'CULTURE' where name = 'Plac Litewski';
    update point_of_interest set category = 'NATURE' where name = 'Ogród Saski';
    update point_of_interest set category = 'CULTURE' where name = 'Centrum Spotkania Kultur';
    update point_of_interest set category = 'NATURE' where name = 'Ogród Botaniczny UMCS';
    update point_of_interest set category = 'CULTURE' where name = 'Muzeum Wsi Lubelskiej';
    update point_of_interest set category = 'RECREATION' where name = 'Plaża nad Zalewem Zemborzyckim';
    update point_of_interest set category = 'NATURE' where name = 'Rezerwat Stary Gaj';
    update point_of_interest set category = 'VIEWPOINT' where name = 'Górki Czechowskie - punkt widokowy';
    update point_of_interest set category = 'CULTURE' where name = 'Mural na Zielonej';
    update point_of_interest set category = 'VIEWPOINT' where name = 'Punkt widokowy nad Bystrzycą';

    -- konta testowe (profil tworzy trigger z auth.users)
    insert into auth.users (instance_id, id, aud, role, email, encrypted_password, email_confirmed_at,
                            raw_app_meta_data, raw_user_meta_data, created_at, updated_at,
                            confirmation_token, recovery_token, email_change_token_new, email_change)
    values ('00000000-0000-0000-0000-000000000000', '5eed0000-0000-4000-8000-000000000001'::uuid, 'authenticated', 'authenticated', 'seed.anna@trailify.test', '', now(),
            '{"provider":"email","providers":["email"]}'::jsonb, jsonb_build_object('display_name', 'Anna Kowalska'),
            now(), now(), '', '', '', '');
    update profile set avatar_url = 'https://picsum.photos/seed/trailify-user-anna/200/200', is_private = false where id = '5eed0000-0000-4000-8000-000000000001'::uuid;
    insert into auth.users (instance_id, id, aud, role, email, encrypted_password, email_confirmed_at,
                            raw_app_meta_data, raw_user_meta_data, created_at, updated_at,
                            confirmation_token, recovery_token, email_change_token_new, email_change)
    values ('00000000-0000-0000-0000-000000000000', '5eed0000-0000-4000-8000-000000000002'::uuid, 'authenticated', 'authenticated', 'seed.piotr@trailify.test', '', now(),
            '{"provider":"email","providers":["email"]}'::jsonb, jsonb_build_object('display_name', 'Piotr Nowak'),
            now(), now(), '', '', '', '');
    update profile set avatar_url = 'https://picsum.photos/seed/trailify-user-piotr/200/200', is_private = false where id = '5eed0000-0000-4000-8000-000000000002'::uuid;
    insert into auth.users (instance_id, id, aud, role, email, encrypted_password, email_confirmed_at,
                            raw_app_meta_data, raw_user_meta_data, created_at, updated_at,
                            confirmation_token, recovery_token, email_change_token_new, email_change)
    values ('00000000-0000-0000-0000-000000000000', '5eed0000-0000-4000-8000-000000000003'::uuid, 'authenticated', 'authenticated', 'seed.marta@trailify.test', '', now(),
            '{"provider":"email","providers":["email"]}'::jsonb, jsonb_build_object('display_name', 'Marta Wiśniewska'),
            now(), now(), '', '', '', '');
    update profile set avatar_url = 'https://picsum.photos/seed/trailify-user-marta/200/200', is_private = false where id = '5eed0000-0000-4000-8000-000000000003'::uuid;
    insert into auth.users (instance_id, id, aud, role, email, encrypted_password, email_confirmed_at,
                            raw_app_meta_data, raw_user_meta_data, created_at, updated_at,
                            confirmation_token, recovery_token, email_change_token_new, email_change)
    values ('00000000-0000-0000-0000-000000000000', '5eed0000-0000-4000-8000-000000000004'::uuid, 'authenticated', 'authenticated', 'seed.tomek@trailify.test', '', now(),
            '{"provider":"email","providers":["email"]}'::jsonb, jsonb_build_object('display_name', 'Tomek Zieliński'),
            now(), now(), '', '', '', '');
    update profile set avatar_url = 'https://picsum.photos/seed/trailify-user-tomek/200/200', is_private = false where id = '5eed0000-0000-4000-8000-000000000004'::uuid;
    insert into auth.users (instance_id, id, aud, role, email, encrypted_password, email_confirmed_at,
                            raw_app_meta_data, raw_user_meta_data, created_at, updated_at,
                            confirmation_token, recovery_token, email_change_token_new, email_change)
    values ('00000000-0000-0000-0000-000000000000', '5eed0000-0000-4000-8000-000000000005'::uuid, 'authenticated', 'authenticated', 'seed.ola@trailify.test', '', now(),
            '{"provider":"email","providers":["email"]}'::jsonb, jsonb_build_object('display_name', 'Ola Lewandowska'),
            now(), now(), '', '', '', '');
    update profile set avatar_url = 'https://picsum.photos/seed/trailify-user-ola/200/200', is_private = true where id = '5eed0000-0000-4000-8000-000000000005'::uuid;
    insert into auth.users (instance_id, id, aud, role, email, encrypted_password, email_confirmed_at,
                            raw_app_meta_data, raw_user_meta_data, created_at, updated_at,
                            confirmation_token, recovery_token, email_change_token_new, email_change)
    values ('00000000-0000-0000-0000-000000000000', '5eed0000-0000-4000-8000-000000000006'::uuid, 'authenticated', 'authenticated', 'seed.kuba@trailify.test', '', now(),
            '{"provider":"email","providers":["email"]}'::jsonb, jsonb_build_object('display_name', 'Kuba Mazur'),
            now(), now(), '', '', '', '');
    update profile set avatar_url = 'https://picsum.photos/seed/trailify-user-kuba/200/200', is_private = false where id = '5eed0000-0000-4000-8000-000000000006'::uuid;
    insert into auth.users (instance_id, id, aud, role, email, encrypted_password, email_confirmed_at,
                            raw_app_meta_data, raw_user_meta_data, created_at, updated_at,
                            confirmation_token, recovery_token, email_change_token_new, email_change)
    values ('00000000-0000-0000-0000-000000000000', '5eed0000-0000-4000-8000-000000000007'::uuid, 'authenticated', 'authenticated', 'seed.ewa@trailify.test', '', now(),
            '{"provider":"email","providers":["email"]}'::jsonb, jsonb_build_object('display_name', 'Ewa Dąbrowska'),
            now(), now(), '', '', '', '');
    update profile set avatar_url = 'https://picsum.photos/seed/trailify-user-ewa/200/200', is_private = false where id = '5eed0000-0000-4000-8000-000000000007'::uuid;
    insert into auth.users (instance_id, id, aud, role, email, encrypted_password, email_confirmed_at,
                            raw_app_meta_data, raw_user_meta_data, created_at, updated_at,
                            confirmation_token, recovery_token, email_change_token_new, email_change)
    values ('00000000-0000-0000-0000-000000000000', '5eed0000-0000-4000-8000-000000000008'::uuid, 'authenticated', 'authenticated', 'seed.marek@trailify.test', '', now(),
            '{"provider":"email","providers":["email"]}'::jsonb, jsonb_build_object('display_name', 'Marek Kaczmarek'),
            now(), now(), '', '', '', '');
    update profile set avatar_url = 'https://picsum.photos/seed/trailify-user-marek/200/200', is_private = false where id = '5eed0000-0000-4000-8000-000000000008'::uuid;

    -- obserwowanie
    insert into follow (follower_id, followee_id, created_at) values (me, '5eed0000-0000-4000-8000-000000000001'::uuid, now() - interval '30 days');
    insert into follow (follower_id, followee_id, created_at) values (me, '5eed0000-0000-4000-8000-000000000002'::uuid, now() - interval '29 days');
    insert into follow (follower_id, followee_id, created_at) values (me, '5eed0000-0000-4000-8000-000000000003'::uuid, now() - interval '28 days');
    insert into follow (follower_id, followee_id, created_at) values (me, '5eed0000-0000-4000-8000-000000000004'::uuid, now() - interval '27 days');
    insert into follow (follower_id, followee_id, created_at) values (me, '5eed0000-0000-4000-8000-000000000005'::uuid, now() - interval '26 days');
    insert into follow (follower_id, followee_id, created_at) values ('5eed0000-0000-4000-8000-000000000001'::uuid, me, now() - interval '30 days');
    insert into follow (follower_id, followee_id, created_at) values ('5eed0000-0000-4000-8000-000000000001'::uuid, '5eed0000-0000-4000-8000-000000000002'::uuid, now() - interval '29 days');
    insert into follow (follower_id, followee_id, created_at) values ('5eed0000-0000-4000-8000-000000000001'::uuid, '5eed0000-0000-4000-8000-000000000003'::uuid, now() - interval '28 days');
    insert into follow (follower_id, followee_id, created_at) values ('5eed0000-0000-4000-8000-000000000002'::uuid, me, now() - interval '30 days');
    insert into follow (follower_id, followee_id, created_at) values ('5eed0000-0000-4000-8000-000000000002'::uuid, '5eed0000-0000-4000-8000-000000000001'::uuid, now() - interval '29 days');
    insert into follow (follower_id, followee_id, created_at) values ('5eed0000-0000-4000-8000-000000000006'::uuid, me, now() - interval '30 days');
    insert into follow (follower_id, followee_id, created_at) values ('5eed0000-0000-4000-8000-000000000006'::uuid, '5eed0000-0000-4000-8000-000000000001'::uuid, now() - interval '29 days');
    insert into follow (follower_id, followee_id, created_at) values ('5eed0000-0000-4000-8000-000000000007'::uuid, me, now() - interval '30 days');
    insert into follow (follower_id, followee_id, created_at) values ('5eed0000-0000-4000-8000-000000000007'::uuid, '5eed0000-0000-4000-8000-000000000003'::uuid, now() - interval '29 days');
    insert into follow (follower_id, followee_id, created_at) values ('5eed0000-0000-4000-8000-000000000008'::uuid, me, now() - interval '30 days');
    insert into follow (follower_id, followee_id, created_at) values ('5eed0000-0000-4000-8000-000000000008'::uuid, '5eed0000-0000-4000-8000-000000000004'::uuid, now() - interval '29 days');
    insert into follow (follower_id, followee_id, created_at) values ('5eed0000-0000-4000-8000-000000000003'::uuid, '5eed0000-0000-4000-8000-000000000001'::uuid, now() - interval '30 days');
    insert into follow (follower_id, followee_id, created_at) values ('5eed0000-0000-4000-8000-000000000004'::uuid, '5eed0000-0000-4000-8000-000000000002'::uuid, now() - interval '30 days');

    -- trasy (wpis na osi czasu tylko dla publicznych)
    insert into route (user_id, name, description, activity_type, is_tourist, is_scenic, is_public,
                       total_distance, duration_seconds, geometry, created_at)
    select '5eed0000-0000-4000-8000-000000000001'::uuid, 'Bulwary i Zalew Zemborzycki', 'Rowerem z centrum wzdłuż Bystrzycy aż do zalewu. Prawie cały czas ścieżką rowerową.', 'CYCLING', true, true, true,
           ST_Length(g::geography), 897, g, now() - interval '12 days 2 hours'
    from ST_SetSRID(ST_GeomFromText('LINESTRING(22.57 51.24, 22.58 51.23, 22.595 51.22, 22.61 51.215, 22.612 51.219)'), 4326) as g;
    insert into photo (route_id, url, storage_key) values ((select id from route where user_id = '5eed0000-0000-4000-8000-000000000001'::uuid and name = 'Bulwary i Zalew Zemborzycki'), 'https://picsum.photos/seed/trailify-anna-12-1/800/600', 'seedsocial/route-anna-12-1.jpg');
    insert into photo (route_id, url, storage_key) values ((select id from route where user_id = '5eed0000-0000-4000-8000-000000000001'::uuid and name = 'Bulwary i Zalew Zemborzycki'), 'https://picsum.photos/seed/trailify-anna-12-2/800/600', 'seedsocial/route-anna-12-2.jpg');
    insert into feed_event (user_id, type, route_id, created_at)
    select user_id, 'ROUTE_PUBLISHED', id, created_at from route where user_id = '5eed0000-0000-4000-8000-000000000001'::uuid and name = 'Bulwary i Zalew Zemborzycki';
    insert into route (user_id, name, description, activity_type, is_tourist, is_scenic, is_public,
                       total_distance, duration_seconds, geometry, created_at)
    select '5eed0000-0000-4000-8000-000000000001'::uuid, 'Wieczorny spacer po Starówce', 'Krótka pętla po Starym Mieście, najładniejsza po zmroku, gdy działa iluminacja.', 'WALKING', true, false, true,
           ST_Length(g::geography), 684, g, now() - interval '5 days 2 hours'
    from ST_SetSRID(ST_GeomFromText('LINESTRING(22.5651 51.2488, 22.5668 51.2482, 22.5688 51.2477, 22.5702 51.2489, 22.5683 51.2497, 22.5662 51.2492, 22.5651 51.2488)'), 4326) as g;
    insert into photo (route_id, url, storage_key) values ((select id from route where user_id = '5eed0000-0000-4000-8000-000000000001'::uuid and name = 'Wieczorny spacer po Starówce'), 'https://picsum.photos/seed/trailify-anna-5-1/800/600', 'seedsocial/route-anna-5-1.jpg');
    insert into feed_event (user_id, type, route_id, created_at)
    select user_id, 'ROUTE_PUBLISHED', id, created_at from route where user_id = '5eed0000-0000-4000-8000-000000000001'::uuid and name = 'Wieczorny spacer po Starówce';
    insert into route (user_id, name, description, activity_type, is_tourist, is_scenic, is_public,
                       total_distance, duration_seconds, geometry, created_at)
    select '5eed0000-0000-4000-8000-000000000002'::uuid, 'Szybka dycha przy Czechowie', 'Płaska pętla na tempo, prawie bez skrzyżowań.', 'RUNNING', false, false, true,
           ST_Length(g::geography), 1123, g, now() - interval '9 days 2 hours'
    from ST_SetSRID(ST_GeomFromText('LINESTRING(22.578 51.261, 22.575 51.265, 22.578 51.269, 22.585 51.27, 22.59 51.267, 22.587 51.263, 22.578 51.261)'), 4326) as g;
    insert into feed_event (user_id, type, route_id, created_at)
    select user_id, 'ROUTE_PUBLISHED', id, created_at from route where user_id = '5eed0000-0000-4000-8000-000000000002'::uuid and name = 'Szybka dycha przy Czechowie';
    insert into route (user_id, name, description, activity_type, is_tourist, is_scenic, is_public,
                       total_distance, duration_seconds, geometry, created_at)
    select '5eed0000-0000-4000-8000-000000000002'::uuid, 'Rowerem do Świdnika', 'Spokojna trasa na wschód, większość po ścieżkach rowerowych.', 'CYCLING', false, false, true,
           ST_Length(g::geography), 1811, g, now() - interval '3 days 2 hours'
    from ST_SetSRID(ST_GeomFromText('LINESTRING(22.575 51.246, 22.6 51.242, 22.635 51.235, 22.67 51.227, 22.6975 51.219)'), 4326) as g;
    insert into feed_event (user_id, type, route_id, created_at)
    select user_id, 'ROUTE_PUBLISHED', id, created_at from route where user_id = '5eed0000-0000-4000-8000-000000000002'::uuid and name = 'Rowerem do Świdnika';
    insert into route (user_id, name, description, activity_type, is_tourist, is_scenic, is_public,
                       total_distance, duration_seconds, geometry, created_at)
    select '5eed0000-0000-4000-8000-000000000003'::uuid, 'Las Dąbrowa - leśna pętla', 'Cicha pętla wśród drzew, cień nawet w upalne dni.', 'WALKING', false, true, true,
           ST_Length(g::geography), 2074, g, now() - interval '7 days 2 hours'
    from ST_SetSRID(ST_GeomFromText('LINESTRING(22.532 51.234, 22.527 51.231, 22.529 51.228, 22.535 51.227, 22.54 51.23, 22.538 51.2335, 22.532 51.234)'), 4326) as g;
    insert into photo (route_id, url, storage_key) values ((select id from route where user_id = '5eed0000-0000-4000-8000-000000000003'::uuid and name = 'Las Dąbrowa - leśna pętla'), 'https://picsum.photos/seed/trailify-marta-7-1/800/600', 'seedsocial/route-marta-7-1.jpg');
    insert into photo (route_id, url, storage_key) values ((select id from route where user_id = '5eed0000-0000-4000-8000-000000000003'::uuid and name = 'Las Dąbrowa - leśna pętla'), 'https://picsum.photos/seed/trailify-marta-7-2/800/600', 'seedsocial/route-marta-7-2.jpg');
    insert into feed_event (user_id, type, route_id, created_at)
    select user_id, 'ROUTE_PUBLISHED', id, created_at from route where user_id = '5eed0000-0000-4000-8000-000000000003'::uuid and name = 'Las Dąbrowa - leśna pętla';
    insert into route (user_id, name, description, activity_type, is_tourist, is_scenic, is_public,
                       total_distance, duration_seconds, geometry, created_at)
    select '5eed0000-0000-4000-8000-000000000003'::uuid, 'Do pracy rowerem (prywatna)', 'Codzienny dojazd, nieudostępniona.', 'CYCLING', false, false, false,
           ST_Length(g::geography), 476, g, now() - interval '20 days 2 hours'
    from ST_SetSRID(ST_GeomFromText('LINESTRING(22.53 51.238, 22.54 51.242, 22.55 51.246, 22.56 51.248)'), 4326) as g;
    insert into route (user_id, name, description, activity_type, is_tourist, is_scenic, is_public,
                       total_distance, duration_seconds, geometry, created_at)
    select '5eed0000-0000-4000-8000-000000000004'::uuid, 'Lublin - Kozłówka', 'Dłuższa wycieczka do pałacu Zamoyskich. Końcówka szutrowa.', 'CYCLING', true, false, true,
           ST_Length(g::geography), 3989, g, now() - interval '15 days 2 hours'
    from ST_SetSRID(ST_GeomFromText('LINESTRING(22.55 51.247, 22.61 51.265, 22.66 51.285, 22.72 51.31, 22.76 51.33, 22.789 51.345)'), 4326) as g;
    insert into photo (route_id, url, storage_key) values ((select id from route where user_id = '5eed0000-0000-4000-8000-000000000004'::uuid and name = 'Lublin - Kozłówka'), 'https://picsum.photos/seed/trailify-tomek-15-1/800/600', 'seedsocial/route-tomek-15-1.jpg');
    insert into feed_event (user_id, type, route_id, created_at)
    select user_id, 'ROUTE_PUBLISHED', id, created_at from route where user_id = '5eed0000-0000-4000-8000-000000000004'::uuid and name = 'Lublin - Kozłówka';
    insert into route (user_id, name, description, activity_type, is_tourist, is_scenic, is_public,
                       total_distance, duration_seconds, geometry, created_at)
    select '5eed0000-0000-4000-8000-000000000005'::uuid, 'Poranny bieg wokół kampusu', 'Kilka kółek wokół UMCS (profil prywatny, trasa niewidoczna dla innych).', 'RUNNING', false, false, true,
           ST_Length(g::geography), 569, g, now() - interval '4 days 2 hours'
    from ST_SetSRID(ST_GeomFromText('LINESTRING(22.542 51.248, 22.546 51.25, 22.55 51.248, 22.546 51.245, 22.542 51.248)'), 4326) as g;
    insert into feed_event (user_id, type, route_id, created_at)
    select user_id, 'ROUTE_PUBLISHED', id, created_at from route where user_id = '5eed0000-0000-4000-8000-000000000005'::uuid and name = 'Poranny bieg wokół kampusu';
    insert into route (user_id, name, description, activity_type, is_tourist, is_scenic, is_public,
                       total_distance, duration_seconds, geometry, created_at)
    select '5eed0000-0000-4000-8000-000000000006'::uuid, 'Spacer nad Czechówką', 'Spokojna ścieżka wzdłuż rzeki, dobra na krótki wypad.', 'WALKING', false, true, true,
           ST_Length(g::geography), 1187, g, now() - interval '6 days 2 hours'
    from ST_SetSRID(ST_GeomFromText('LINESTRING(22.56 51.258, 22.565 51.256, 22.572 51.254, 22.579 51.252)'), 4326) as g;
    insert into photo (route_id, url, storage_key) values ((select id from route where user_id = '5eed0000-0000-4000-8000-000000000006'::uuid and name = 'Spacer nad Czechówką'), 'https://picsum.photos/seed/trailify-kuba-6-1/800/600', 'seedsocial/route-kuba-6-1.jpg');
    insert into feed_event (user_id, type, route_id, created_at)
    select user_id, 'ROUTE_PUBLISHED', id, created_at from route where user_id = '5eed0000-0000-4000-8000-000000000006'::uuid and name = 'Spacer nad Czechówką';
    insert into route (user_id, name, description, activity_type, is_tourist, is_scenic, is_public,
                       total_distance, duration_seconds, geometry, created_at)
    select '5eed0000-0000-4000-8000-000000000007'::uuid, 'Interwały nad Zalewem', 'Prosty odcinek przy plaży do powtórek.', 'RUNNING', false, false, true,
           ST_Length(g::geography), 1026, g, now() - interval '8 days 2 hours'
    from ST_SetSRID(ST_GeomFromText('LINESTRING(22.612 51.216, 22.62 51.212, 22.628 51.208, 22.62 51.212, 22.612 51.216)'), 4326) as g;
    insert into feed_event (user_id, type, route_id, created_at)
    select user_id, 'ROUTE_PUBLISHED', id, created_at from route where user_id = '5eed0000-0000-4000-8000-000000000007'::uuid and name = 'Interwały nad Zalewem';

    -- aktywności (udostępnione dostają wpis na osi czasu)
    insert into activity (client_id, user_id, route_id, type, start_time, end_time, distance, duration_seconds,
                          avg_pace, step_count, geometry, is_shared)
    select '5eed0001-0000-4000-8000-00000000c11d', '5eed0000-0000-4000-8000-000000000001'::uuid, (select id from route where user_id = '5eed0000-0000-4000-8000-000000000001'::uuid and name = 'Wieczorny spacer po Starówce'), 'WALKING', now() - interval '4 days' - interval '5 hours', now() - interval '4 days' - interval '5 hours' + interval '711 seconds',
           ST_Length(g::geography), 711, 13.86, 1140, g, true
    from ST_SetSRID(ST_GeomFromText('LINESTRING(22.5651 51.2488, 22.5668 51.2482, 22.5688 51.2477, 22.5702 51.2489, 22.5683 51.2497, 22.5662 51.2492, 22.5651 51.2488)'), 4326) as g;
    insert into feed_event (user_id, type, activity_id, created_at)
    select user_id, 'ACTIVITY', id, end_time from activity where user_id = '5eed0000-0000-4000-8000-000000000001'::uuid and client_id = '5eed0001-0000-4000-8000-00000000c11d';
    insert into activity (client_id, user_id, route_id, type, start_time, end_time, distance, duration_seconds,
                          avg_pace, step_count, geometry, is_shared)
    select '5eed0002-0000-4000-8000-00000000c11d', '5eed0000-0000-4000-8000-000000000001'::uuid, (select id from route where user_id = '5eed0000-0000-4000-8000-000000000001'::uuid and name = 'Bulwary i Zalew Zemborzycki'), 'CYCLING', now() - interval '11 days' - interval '14 hours', now() - interval '11 days' - interval '14 hours' + interval '969 seconds',
           ST_Length(g::geography), 969, 3.6, null, g, true
    from ST_SetSRID(ST_GeomFromText('LINESTRING(22.57 51.24, 22.58 51.23, 22.595 51.22, 22.61 51.215, 22.612 51.219)'), 4326) as g;
    insert into feed_event (user_id, type, activity_id, created_at)
    select user_id, 'ACTIVITY', id, end_time from activity where user_id = '5eed0000-0000-4000-8000-000000000001'::uuid and client_id = '5eed0002-0000-4000-8000-00000000c11d';
    insert into activity (client_id, user_id, route_id, type, start_time, end_time, distance, duration_seconds,
                          avg_pace, step_count, geometry, is_shared)
    select '5eed0003-0000-4000-8000-00000000c11d', '5eed0000-0000-4000-8000-000000000001'::uuid, null, 'RUNNING', now() - interval '6 days' - interval '17 hours', now() - interval '6 days' - interval '17 hours' + interval '863 seconds',
           ST_Length(g::geography), 863, 6.71, 2142, g, false
    from ST_SetSRID(ST_GeomFromText('LINESTRING(22.55 51.245, 22.557 51.247, 22.564 51.249, 22.557 51.247, 22.55 51.245)'), 4326) as g;
    insert into activity (client_id, user_id, route_id, type, start_time, end_time, distance, duration_seconds,
                          avg_pace, step_count, geometry, is_shared)
    select '5eed0004-0000-4000-8000-00000000c11d', '5eed0000-0000-4000-8000-000000000002'::uuid, (select id from route where user_id = '5eed0000-0000-4000-8000-000000000002'::uuid and name = 'Szybka dycha przy Czechowie'), 'RUNNING', now() - interval '8 days' - interval '18 hours', now() - interval '8 days' - interval '18 hours' + interval '1302 seconds',
           ST_Length(g::geography), 1302, 6.96, 3119, g, true
    from ST_SetSRID(ST_GeomFromText('LINESTRING(22.578 51.261, 22.575 51.265, 22.578 51.269, 22.585 51.27, 22.59 51.267, 22.587 51.263, 22.578 51.261)'), 4326) as g;
    insert into feed_event (user_id, type, activity_id, created_at)
    select user_id, 'ACTIVITY', id, end_time from activity where user_id = '5eed0000-0000-4000-8000-000000000002'::uuid and client_id = '5eed0004-0000-4000-8000-00000000c11d';
    insert into activity (client_id, user_id, route_id, type, start_time, end_time, distance, duration_seconds,
                          avg_pace, step_count, geometry, is_shared)
    select '5eed0005-0000-4000-8000-00000000c11d', '5eed0000-0000-4000-8000-000000000002'::uuid, null, 'CYCLING', now() - interval '2 days' - interval '7 hours', now() - interval '2 days' - interval '7 hours' + interval '534 seconds',
           ST_Length(g::geography), 534, 3.33, null, g, true
    from ST_SetSRID(ST_GeomFromText('LINESTRING(22.6 51.22, 22.61 51.215, 22.62 51.21, 22.61 51.215)'), 4326) as g;
    insert into feed_event (user_id, type, activity_id, created_at)
    select user_id, 'ACTIVITY', id, end_time from activity where user_id = '5eed0000-0000-4000-8000-000000000002'::uuid and client_id = '5eed0005-0000-4000-8000-00000000c11d';
    insert into activity (client_id, user_id, route_id, type, start_time, end_time, distance, duration_seconds,
                          avg_pace, step_count, geometry, is_shared)
    select '5eed0006-0000-4000-8000-00000000c11d', '5eed0000-0000-4000-8000-000000000002'::uuid, null, 'WALKING', now() - interval '5 days' - interval '12 hours', now() - interval '5 days' - interval '12 hours' + interval '685 seconds',
           ST_Length(g::geography), 685, 13.87, 1097, g, false
    from ST_SetSRID(ST_GeomFromText('LINESTRING(22.5617 51.2484, 22.565 51.2481, 22.5675 51.2477, 22.565 51.2481, 22.5617 51.2484)'), 4326) as g;
    insert into activity (client_id, user_id, route_id, type, start_time, end_time, distance, duration_seconds,
                          avg_pace, step_count, geometry, is_shared)
    select '5eed0007-0000-4000-8000-00000000c11d', '5eed0000-0000-4000-8000-000000000003'::uuid, (select id from route where user_id = '5eed0000-0000-4000-8000-000000000003'::uuid and name = 'Las Dąbrowa - leśna pętla'), 'WALKING', now() - interval '6 days' - interval '9 hours', now() - interval '6 days' - interval '9 hours' + interval '2240 seconds',
           ST_Length(g::geography), 2240, 14.4, 3457, g, true
    from ST_SetSRID(ST_GeomFromText('LINESTRING(22.532 51.234, 22.527 51.231, 22.529 51.228, 22.535 51.227, 22.54 51.23, 22.538 51.2335, 22.532 51.234)'), 4326) as g;
    insert into feed_event (user_id, type, activity_id, created_at)
    select user_id, 'ACTIVITY', id, end_time from activity where user_id = '5eed0000-0000-4000-8000-000000000003'::uuid and client_id = '5eed0007-0000-4000-8000-00000000c11d';
    insert into activity (client_id, user_id, route_id, type, start_time, end_time, distance, duration_seconds,
                          avg_pace, step_count, geometry, is_shared)
    select '5eed0008-0000-4000-8000-00000000c11d', '5eed0000-0000-4000-8000-000000000003'::uuid, (select id from route where user_id = '5eed0000-0000-4000-8000-000000000001'::uuid and name = 'Wieczorny spacer po Starówce'), 'WALKING', now() - interval '3 days' - interval '4 hours', now() - interval '3 days' - interval '4 hours' + interval '766 seconds',
           ST_Length(g::geography), 766, 14.93, 1140, g, true
    from ST_SetSRID(ST_GeomFromText('LINESTRING(22.5651 51.2488, 22.5668 51.2482, 22.5688 51.2477, 22.5702 51.2489, 22.5683 51.2497, 22.5662 51.2492, 22.5651 51.2488)'), 4326) as g;
    insert into feed_event (user_id, type, activity_id, created_at)
    select user_id, 'ACTIVITY', id, end_time from activity where user_id = '5eed0000-0000-4000-8000-000000000003'::uuid and client_id = '5eed0008-0000-4000-8000-00000000c11d';
    insert into activity (client_id, user_id, route_id, type, start_time, end_time, distance, duration_seconds,
                          avg_pace, step_count, geometry, is_shared)
    select '5eed0009-0000-4000-8000-00000000c11d', '5eed0000-0000-4000-8000-000000000004'::uuid, (select id from route where user_id = '5eed0000-0000-4000-8000-000000000004'::uuid and name = 'Lublin - Kozłówka'), 'CYCLING', now() - interval '14 days' - interval '16 hours', now() - interval '14 days' - interval '16 hours' + interval '4627 seconds',
           ST_Length(g::geography), 4627, 3.87, null, g, true
    from ST_SetSRID(ST_GeomFromText('LINESTRING(22.55 51.247, 22.61 51.265, 22.66 51.285, 22.72 51.31, 22.76 51.33, 22.789 51.345)'), 4326) as g;
    insert into feed_event (user_id, type, activity_id, created_at)
    select user_id, 'ACTIVITY', id, end_time from activity where user_id = '5eed0000-0000-4000-8000-000000000004'::uuid and client_id = '5eed0009-0000-4000-8000-00000000c11d';
    insert into activity (client_id, user_id, route_id, type, start_time, end_time, distance, duration_seconds,
                          avg_pace, step_count, geometry, is_shared)
    select '5eed0010-0000-4000-8000-00000000c11d', '5eed0000-0000-4000-8000-000000000004'::uuid, null, 'RUNNING', now() - interval '3 days' - interval '18 hours', now() - interval '3 days' - interval '18 hours' + interval '771 seconds',
           ST_Length(g::geography), 771, 6.0, 2142, g, false
    from ST_SetSRID(ST_GeomFromText('LINESTRING(22.55 51.245, 22.557 51.247, 22.564 51.249, 22.557 51.247, 22.55 51.245)'), 4326) as g;
    insert into activity (client_id, user_id, route_id, type, start_time, end_time, distance, duration_seconds,
                          avg_pace, step_count, geometry, is_shared)
    select '5eed0011-0000-4000-8000-00000000c11d', '5eed0000-0000-4000-8000-000000000006'::uuid, (select id from route where user_id = '5eed0000-0000-4000-8000-000000000001'::uuid and name = 'Wieczorny spacer po Starówce'), 'WALKING', now() - interval '1 days' - interval '6 hours', now() - interval '1 days' - interval '6 hours' + interval '711 seconds',
           ST_Length(g::geography), 711, 13.86, 1140, g, true
    from ST_SetSRID(ST_GeomFromText('LINESTRING(22.5651 51.2488, 22.5668 51.2482, 22.5688 51.2477, 22.5702 51.2489, 22.5683 51.2497, 22.5662 51.2492, 22.5651 51.2488)'), 4326) as g;
    insert into feed_event (user_id, type, activity_id, created_at)
    select user_id, 'ACTIVITY', id, end_time from activity where user_id = '5eed0000-0000-4000-8000-000000000006'::uuid and client_id = '5eed0011-0000-4000-8000-00000000c11d';
    insert into activity (client_id, user_id, route_id, type, start_time, end_time, distance, duration_seconds,
                          avg_pace, step_count, geometry, is_shared)
    select '5eed0012-0000-4000-8000-00000000c11d', '5eed0000-0000-4000-8000-000000000005'::uuid, (select id from route where user_id = '5eed0000-0000-4000-8000-000000000005'::uuid and name = 'Poranny bieg wokół kampusu'), 'RUNNING', now() - interval '4 days' - interval '17 hours', now() - interval '4 days' - interval '17 hours' + interval '614 seconds',
           ST_Length(g::geography), 614, 6.47, 1581, g, true
    from ST_SetSRID(ST_GeomFromText('LINESTRING(22.542 51.248, 22.546 51.25, 22.55 51.248, 22.546 51.245, 22.542 51.248)'), 4326) as g;
    insert into feed_event (user_id, type, activity_id, created_at)
    select user_id, 'ACTIVITY', id, end_time from activity where user_id = '5eed0000-0000-4000-8000-000000000005'::uuid and client_id = '5eed0012-0000-4000-8000-00000000c11d';
    insert into activity (client_id, user_id, route_id, type, start_time, end_time, distance, duration_seconds,
                          avg_pace, step_count, geometry, is_shared)
    select '5eed0013-0000-4000-8000-00000000c11d', me, (select id from route where user_id = '5eed0000-0000-4000-8000-000000000001'::uuid and name = 'Wieczorny spacer po Starówce'), 'WALKING', now() - interval '3 days' - interval '5 hours', now() - interval '3 days' - interval '5 hours' + interval '766 seconds',
           ST_Length(g::geography), 766, 14.93, 1140, g, true
    from ST_SetSRID(ST_GeomFromText('LINESTRING(22.5651 51.2488, 22.5668 51.2482, 22.5688 51.2477, 22.5702 51.2489, 22.5683 51.2497, 22.5662 51.2492, 22.5651 51.2488)'), 4326) as g;
    insert into feed_event (user_id, type, activity_id, created_at)
    select user_id, 'ACTIVITY', id, end_time from activity where user_id = me and client_id = '5eed0013-0000-4000-8000-00000000c11d';
    insert into activity (client_id, user_id, route_id, type, start_time, end_time, distance, duration_seconds,
                          avg_pace, step_count, geometry, is_shared)
    select '5eed0014-0000-4000-8000-00000000c11d', me, null, 'RUNNING', now() - interval '2 days' - interval '17 hours', now() - interval '2 days' - interval '17 hours' + interval '832 seconds',
           ST_Length(g::geography), 832, 6.96, 1992, g, false
    from ST_SetSRID(ST_GeomFromText('LINESTRING(22.54 51.27, 22.543 51.273, 22.547 51.276, 22.55 51.273, 22.54 51.27)'), 4326) as g;
    insert into activity (client_id, user_id, route_id, type, start_time, end_time, distance, duration_seconds,
                          avg_pace, step_count, geometry, is_shared)
    select '5eed0015-0000-4000-8000-00000000c11d', me, null, 'WALKING', now() - interval '6 days' - interval '8 hours', now() - interval '6 days' - interval '8 hours' + interval '871 seconds',
           ST_Length(g::geography), 871, 13.32, 1453, g, true
    from ST_SetSRID(ST_GeomFromText('LINESTRING(22.5585 51.2492, 22.5565 51.251, 22.5545 51.2525, 22.553 51.251, 22.5585 51.2492)'), 4326) as g;
    insert into feed_event (user_id, type, activity_id, created_at)
    select user_id, 'ACTIVITY', id, end_time from activity where user_id = me and client_id = '5eed0015-0000-4000-8000-00000000c11d';
    insert into activity (client_id, user_id, route_id, type, start_time, end_time, distance, duration_seconds,
                          avg_pace, step_count, geometry, is_shared)
    select '5eed0016-0000-4000-8000-00000000c11d', '5eed0000-0000-4000-8000-000000000002'::uuid, (select id from route where user_id = '5eed0000-0000-4000-8000-000000000001'::uuid and name = 'Bulwary i Zalew Zemborzycki'), 'CYCLING', now() - interval '11 days' - interval '12 hours', now() - interval '11 days' - interval '12 hours' + interval '933 seconds',
           ST_Length(g::geography), 933, 3.47, null, g, false
    from ST_SetSRID(ST_GeomFromText('LINESTRING(22.57 51.24, 22.58 51.23, 22.595 51.22, 22.61 51.215, 22.612 51.219)'), 4326) as g;
    insert into activity (client_id, user_id, route_id, type, start_time, end_time, distance, duration_seconds,
                          avg_pace, step_count, geometry, is_shared)
    select '5eed0017-0000-4000-8000-00000000c11d', '5eed0000-0000-4000-8000-000000000004'::uuid, (select id from route where user_id = '5eed0000-0000-4000-8000-000000000001'::uuid and name = 'Bulwary i Zalew Zemborzycki'), 'CYCLING', now() - interval '10 days' - interval '12 hours', now() - interval '10 days' - interval '12 hours' + interval '969 seconds',
           ST_Length(g::geography), 969, 3.6, null, g, false
    from ST_SetSRID(ST_GeomFromText('LINESTRING(22.57 51.24, 22.58 51.23, 22.595 51.22, 22.61 51.215, 22.612 51.219)'), 4326) as g;
    insert into activity (client_id, user_id, route_id, type, start_time, end_time, distance, duration_seconds,
                          avg_pace, step_count, geometry, is_shared)
    select '5eed0018-0000-4000-8000-00000000c11d', '5eed0000-0000-4000-8000-000000000007'::uuid, (select id from route where user_id = '5eed0000-0000-4000-8000-000000000003'::uuid and name = 'Las Dąbrowa - leśna pętla'), 'WALKING', now() - interval '6 days' - interval '12 hours', now() - interval '6 days' - interval '12 hours' + interval '2323 seconds',
           ST_Length(g::geography), 2323, 14.93, 3457, g, false
    from ST_SetSRID(ST_GeomFromText('LINESTRING(22.532 51.234, 22.527 51.231, 22.529 51.228, 22.535 51.227, 22.54 51.23, 22.538 51.2335, 22.532 51.234)'), 4326) as g;
    insert into activity (client_id, user_id, route_id, type, start_time, end_time, distance, duration_seconds,
                          avg_pace, step_count, geometry, is_shared)
    select '5eed0019-0000-4000-8000-00000000c11d', me, (select id from route where user_id = '5eed0000-0000-4000-8000-000000000003'::uuid and name = 'Las Dąbrowa - leśna pętla'), 'WALKING', now() - interval '5 days' - interval '12 hours', now() - interval '5 days' - interval '12 hours' + interval '2406 seconds',
           ST_Length(g::geography), 2406, 15.47, 3457, g, false
    from ST_SetSRID(ST_GeomFromText('LINESTRING(22.532 51.234, 22.527 51.231, 22.529 51.228, 22.535 51.227, 22.54 51.23, 22.538 51.2335, 22.532 51.234)'), 4326) as g;
    insert into activity (client_id, user_id, route_id, type, start_time, end_time, distance, duration_seconds,
                          avg_pace, step_count, geometry, is_shared)
    select '5eed0020-0000-4000-8000-00000000c11d', '5eed0000-0000-4000-8000-000000000002'::uuid, (select id from route where user_id = '5eed0000-0000-4000-8000-000000000004'::uuid and name = 'Lublin - Kozłówka'), 'CYCLING', now() - interval '13 days' - interval '12 hours', now() - interval '13 days' - interval '12 hours' + interval '3989 seconds',
           ST_Length(g::geography), 3989, 3.33, null, g, false
    from ST_SetSRID(ST_GeomFromText('LINESTRING(22.55 51.247, 22.61 51.265, 22.66 51.285, 22.72 51.31, 22.76 51.33, 22.789 51.345)'), 4326) as g;
    insert into activity (client_id, user_id, route_id, type, start_time, end_time, distance, duration_seconds,
                          avg_pace, step_count, geometry, is_shared)
    select '5eed0021-0000-4000-8000-00000000c11d', '5eed0000-0000-4000-8000-000000000008'::uuid, (select id from route where user_id = '5eed0000-0000-4000-8000-000000000004'::uuid and name = 'Lublin - Kozłówka'), 'CYCLING', now() - interval '12 days' - interval '12 hours', now() - interval '12 days' - interval '12 hours' + interval '4148 seconds',
           ST_Length(g::geography), 4148, 3.47, null, g, false
    from ST_SetSRID(ST_GeomFromText('LINESTRING(22.55 51.247, 22.61 51.265, 22.66 51.285, 22.72 51.31, 22.76 51.33, 22.789 51.345)'), 4326) as g;
    insert into activity (client_id, user_id, route_id, type, start_time, end_time, distance, duration_seconds,
                          avg_pace, step_count, geometry, is_shared)
    select '5eed0022-0000-4000-8000-00000000c11d', '5eed0000-0000-4000-8000-000000000001'::uuid, (select id from route where user_id = '5eed0000-0000-4000-8000-000000000002'::uuid and name = 'Szybka dycha przy Czechowie'), 'RUNNING', now() - interval '8 days' - interval '12 hours', now() - interval '8 days' - interval '12 hours' + interval '1213 seconds',
           ST_Length(g::geography), 1213, 6.48, 3119, g, false
    from ST_SetSRID(ST_GeomFromText('LINESTRING(22.578 51.261, 22.575 51.265, 22.578 51.269, 22.585 51.27, 22.59 51.267, 22.587 51.263, 22.578 51.261)'), 4326) as g;

    -- opinie
    insert into review (user_id, route_id, rating, comment, created_at)
    values ('5eed0000-0000-4000-8000-000000000003'::uuid, (select id from route where user_id = '5eed0000-0000-4000-8000-000000000001'::uuid and name = 'Wieczorny spacer po Starówce'), 5, 'Pięknie podświetlona wieczorem, polecam!', now() - interval '3 days 11 hours');
    insert into feed_event (user_id, type, review_id, created_at)
    select user_id, 'REVIEW', id, created_at from review where user_id = '5eed0000-0000-4000-8000-000000000003'::uuid and route_id = (select id from route where user_id = '5eed0000-0000-4000-8000-000000000001'::uuid and name = 'Wieczorny spacer po Starówce');
    insert into review (user_id, route_id, rating, comment, created_at)
    values (me, (select id from route where user_id = '5eed0000-0000-4000-8000-000000000001'::uuid and name = 'Wieczorny spacer po Starówce'), 4, 'Fajna trasa, ale w weekend bywa tłoczno.', now() - interval '3 days 16 hours');
    insert into photo (review_id, url, storage_key) values ((select id from review where user_id = me and route_id = (select id from route where user_id = '5eed0000-0000-4000-8000-000000000001'::uuid and name = 'Wieczorny spacer po Starówce')), 'https://picsum.photos/seed/trailify-review-me-2/800/600', 'seedsocial/review-me-2.jpg');
    insert into feed_event (user_id, type, review_id, created_at)
    select user_id, 'REVIEW', id, created_at from review where user_id = me and route_id = (select id from route where user_id = '5eed0000-0000-4000-8000-000000000001'::uuid and name = 'Wieczorny spacer po Starówce');
    insert into review (user_id, route_id, rating, comment, created_at)
    values ('5eed0000-0000-4000-8000-000000000006'::uuid, (select id from route where user_id = '5eed0000-0000-4000-8000-000000000001'::uuid and name = 'Wieczorny spacer po Starówce'), 5, 'Idealna na pierwszy spacer po Lublinie.', now() - interval '1 days 9 hours');
    insert into feed_event (user_id, type, review_id, created_at)
    select user_id, 'REVIEW', id, created_at from review where user_id = '5eed0000-0000-4000-8000-000000000006'::uuid and route_id = (select id from route where user_id = '5eed0000-0000-4000-8000-000000000001'::uuid and name = 'Wieczorny spacer po Starówce');
    insert into review (user_id, route_id, rating, comment, created_at)
    values ('5eed0000-0000-4000-8000-000000000002'::uuid, (select id from route where user_id = '5eed0000-0000-4000-8000-000000000001'::uuid and name = 'Bulwary i Zalew Zemborzycki'), 4, 'Płasko i szybko, tylko miejscami dziurawy asfalt.', now() - interval '10 days 20 hours');
    insert into feed_event (user_id, type, review_id, created_at)
    select user_id, 'REVIEW', id, created_at from review where user_id = '5eed0000-0000-4000-8000-000000000002'::uuid and route_id = (select id from route where user_id = '5eed0000-0000-4000-8000-000000000001'::uuid and name = 'Bulwary i Zalew Zemborzycki');
    insert into review (user_id, route_id, rating, comment, created_at)
    values ('5eed0000-0000-4000-8000-000000000004'::uuid, (select id from route where user_id = '5eed0000-0000-4000-8000-000000000001'::uuid and name = 'Bulwary i Zalew Zemborzycki'), 5, 'Najlepsza trasa rowerowa w okolicy.', now() - interval '9 days 13 hours');
    insert into photo (review_id, url, storage_key) values ((select id from review where user_id = '5eed0000-0000-4000-8000-000000000004'::uuid and route_id = (select id from route where user_id = '5eed0000-0000-4000-8000-000000000001'::uuid and name = 'Bulwary i Zalew Zemborzycki')), 'https://picsum.photos/seed/trailify-review-tomek-5/800/600', 'seedsocial/review-tomek-5.jpg');
    insert into feed_event (user_id, type, review_id, created_at)
    select user_id, 'REVIEW', id, created_at from review where user_id = '5eed0000-0000-4000-8000-000000000004'::uuid and route_id = (select id from route where user_id = '5eed0000-0000-4000-8000-000000000001'::uuid and name = 'Bulwary i Zalew Zemborzycki');
    insert into review (user_id, route_id, rating, comment, created_at)
    values ('5eed0000-0000-4000-8000-000000000007'::uuid, (select id from route where user_id = '5eed0000-0000-4000-8000-000000000003'::uuid and name = 'Las Dąbrowa - leśna pętla'), 5, 'Cisza i zapach lasu, wrócę tu na pewno.', now() - interval '5 days 7 hours');
    insert into feed_event (user_id, type, review_id, created_at)
    select user_id, 'REVIEW', id, created_at from review where user_id = '5eed0000-0000-4000-8000-000000000007'::uuid and route_id = (select id from route where user_id = '5eed0000-0000-4000-8000-000000000003'::uuid and name = 'Las Dąbrowa - leśna pętla');
    insert into review (user_id, route_id, rating, comment, created_at)
    values (me, (select id from route where user_id = '5eed0000-0000-4000-8000-000000000003'::uuid and name = 'Las Dąbrowa - leśna pętla'), 4, 'Świetna na niedzielny spacer.', now() - interval '4 days 18 hours');
    insert into feed_event (user_id, type, review_id, created_at)
    select user_id, 'REVIEW', id, created_at from review where user_id = me and route_id = (select id from route where user_id = '5eed0000-0000-4000-8000-000000000003'::uuid and name = 'Las Dąbrowa - leśna pętla');
    insert into review (user_id, route_id, rating, comment, created_at)
    values ('5eed0000-0000-4000-8000-000000000002'::uuid, (select id from route where user_id = '5eed0000-0000-4000-8000-000000000004'::uuid and name = 'Lublin - Kozłówka'), 5, 'Warto, ale weź wodę, po drodze mało sklepów.', now() - interval '12 days 3 hours');
    insert into feed_event (user_id, type, review_id, created_at)
    select user_id, 'REVIEW', id, created_at from review where user_id = '5eed0000-0000-4000-8000-000000000002'::uuid and route_id = (select id from route where user_id = '5eed0000-0000-4000-8000-000000000004'::uuid and name = 'Lublin - Kozłówka');
    insert into review (user_id, route_id, rating, comment, created_at)
    values ('5eed0000-0000-4000-8000-000000000008'::uuid, (select id from route where user_id = '5eed0000-0000-4000-8000-000000000004'::uuid and name = 'Lublin - Kozłówka'), 4, 'Końcówka szutrem, ale pałac wynagradza.', now() - interval '11 days 11 hours');
    insert into feed_event (user_id, type, review_id, created_at)
    select user_id, 'REVIEW', id, created_at from review where user_id = '5eed0000-0000-4000-8000-000000000008'::uuid and route_id = (select id from route where user_id = '5eed0000-0000-4000-8000-000000000004'::uuid and name = 'Lublin - Kozłówka');
    insert into review (user_id, route_id, rating, comment, created_at)
    values ('5eed0000-0000-4000-8000-000000000001'::uuid, (select id from route where user_id = '5eed0000-0000-4000-8000-000000000002'::uuid and name = 'Szybka dycha przy Czechowie'), 4, 'Dobra na tempówkę.', now() - interval '7 days 16 hours');
    insert into feed_event (user_id, type, review_id, created_at)
    select user_id, 'REVIEW', id, created_at from review where user_id = '5eed0000-0000-4000-8000-000000000001'::uuid and route_id = (select id from route where user_id = '5eed0000-0000-4000-8000-000000000002'::uuid and name = 'Szybka dycha przy Czechowie');

    -- zebrane punkty
    insert into poi_collection (user_id, poi_id, collected_at)
    values (me, (select id from point_of_interest where name = 'Brama Krakowska'), now() - interval '6 days 5 hours')
    on conflict (user_id, poi_id) do update set collected_at = excluded.collected_at;
    insert into feed_event (user_id, type, collection_id, created_at)
    select user_id, 'POI_COLLECTED', id, collected_at from poi_collection where user_id = me and poi_id = (select id from point_of_interest where name = 'Brama Krakowska')
    on conflict do nothing;
    insert into poi_collection (user_id, poi_id, collected_at)
    values (me, (select id from point_of_interest where name = 'Zamek Lubelski'), now() - interval '2 days 5 hours')
    on conflict (user_id, poi_id) do update set collected_at = excluded.collected_at;
    insert into feed_event (user_id, type, collection_id, created_at)
    select user_id, 'POI_COLLECTED', id, collected_at from poi_collection where user_id = me and poi_id = (select id from point_of_interest where name = 'Zamek Lubelski')
    on conflict do nothing;
    insert into poi_collection (user_id, poi_id, collected_at)
    values ('5eed0000-0000-4000-8000-000000000001'::uuid, (select id from point_of_interest where name = 'Brama Krakowska'), now() - interval '20 days 5 hours')
    on conflict (user_id, poi_id) do update set collected_at = excluded.collected_at;
    insert into feed_event (user_id, type, collection_id, created_at)
    select user_id, 'POI_COLLECTED', id, collected_at from poi_collection where user_id = '5eed0000-0000-4000-8000-000000000001'::uuid and poi_id = (select id from point_of_interest where name = 'Brama Krakowska')
    on conflict do nothing;
    insert into poi_collection (user_id, poi_id, collected_at)
    values ('5eed0000-0000-4000-8000-000000000001'::uuid, (select id from point_of_interest where name = 'Wieża Trynitarska'), now() - interval '18 days 5 hours')
    on conflict (user_id, poi_id) do update set collected_at = excluded.collected_at;
    insert into feed_event (user_id, type, collection_id, created_at)
    select user_id, 'POI_COLLECTED', id, collected_at from poi_collection where user_id = '5eed0000-0000-4000-8000-000000000001'::uuid and poi_id = (select id from point_of_interest where name = 'Wieża Trynitarska')
    on conflict do nothing;
    insert into poi_collection (user_id, poi_id, collected_at)
    values ('5eed0000-0000-4000-8000-000000000001'::uuid, (select id from point_of_interest where name = 'Plac Litewski'), now() - interval '16 days 5 hours')
    on conflict (user_id, poi_id) do update set collected_at = excluded.collected_at;
    insert into feed_event (user_id, type, collection_id, created_at)
    select user_id, 'POI_COLLECTED', id, collected_at from poi_collection where user_id = '5eed0000-0000-4000-8000-000000000001'::uuid and poi_id = (select id from point_of_interest where name = 'Plac Litewski')
    on conflict do nothing;
    insert into poi_collection (user_id, poi_id, collected_at)
    values ('5eed0000-0000-4000-8000-000000000001'::uuid, (select id from point_of_interest where name = 'Ogród Saski'), now() - interval '5 days 5 hours')
    on conflict (user_id, poi_id) do update set collected_at = excluded.collected_at;
    insert into feed_event (user_id, type, collection_id, created_at)
    select user_id, 'POI_COLLECTED', id, collected_at from poi_collection where user_id = '5eed0000-0000-4000-8000-000000000001'::uuid and poi_id = (select id from point_of_interest where name = 'Ogród Saski')
    on conflict do nothing;
    insert into poi_collection (user_id, poi_id, collected_at)
    values ('5eed0000-0000-4000-8000-000000000002'::uuid, (select id from point_of_interest where name = 'Zamek Lubelski'), now() - interval '10 days 5 hours')
    on conflict (user_id, poi_id) do update set collected_at = excluded.collected_at;
    insert into feed_event (user_id, type, collection_id, created_at)
    select user_id, 'POI_COLLECTED', id, collected_at from poi_collection where user_id = '5eed0000-0000-4000-8000-000000000002'::uuid and poi_id = (select id from point_of_interest where name = 'Zamek Lubelski')
    on conflict do nothing;
    insert into poi_collection (user_id, poi_id, collected_at)
    values ('5eed0000-0000-4000-8000-000000000002'::uuid, (select id from point_of_interest where name = 'Górki Czechowskie - punkt widokowy'), now() - interval '4 days 5 hours')
    on conflict (user_id, poi_id) do update set collected_at = excluded.collected_at;
    insert into feed_event (user_id, type, collection_id, created_at)
    select user_id, 'POI_COLLECTED', id, collected_at from poi_collection where user_id = '5eed0000-0000-4000-8000-000000000002'::uuid and poi_id = (select id from point_of_interest where name = 'Górki Czechowskie - punkt widokowy')
    on conflict do nothing;
    insert into poi_collection (user_id, poi_id, collected_at)
    values ('5eed0000-0000-4000-8000-000000000003'::uuid, (select id from point_of_interest where name = 'Ogród Botaniczny UMCS'), now() - interval '9 days 5 hours')
    on conflict (user_id, poi_id) do update set collected_at = excluded.collected_at;
    insert into feed_event (user_id, type, collection_id, created_at)
    select user_id, 'POI_COLLECTED', id, collected_at from poi_collection where user_id = '5eed0000-0000-4000-8000-000000000003'::uuid and poi_id = (select id from point_of_interest where name = 'Ogród Botaniczny UMCS')
    on conflict do nothing;
    insert into poi_collection (user_id, poi_id, collected_at)
    values ('5eed0000-0000-4000-8000-000000000003'::uuid, (select id from point_of_interest where name = 'Rezerwat Stary Gaj'), now() - interval '7 days 5 hours')
    on conflict (user_id, poi_id) do update set collected_at = excluded.collected_at;
    insert into feed_event (user_id, type, collection_id, created_at)
    select user_id, 'POI_COLLECTED', id, collected_at from poi_collection where user_id = '5eed0000-0000-4000-8000-000000000003'::uuid and poi_id = (select id from point_of_interest where name = 'Rezerwat Stary Gaj')
    on conflict do nothing;
    insert into poi_collection (user_id, poi_id, collected_at)
    values ('5eed0000-0000-4000-8000-000000000003'::uuid, (select id from point_of_interest where name = 'Ogród Saski'), now() - interval '3 days 5 hours')
    on conflict (user_id, poi_id) do update set collected_at = excluded.collected_at;
    insert into feed_event (user_id, type, collection_id, created_at)
    select user_id, 'POI_COLLECTED', id, collected_at from poi_collection where user_id = '5eed0000-0000-4000-8000-000000000003'::uuid and poi_id = (select id from point_of_interest where name = 'Ogród Saski')
    on conflict do nothing;
    insert into poi_collection (user_id, poi_id, collected_at)
    values ('5eed0000-0000-4000-8000-000000000004'::uuid, (select id from point_of_interest where name = 'Muzeum Wsi Lubelskiej'), now() - interval '13 days 5 hours')
    on conflict (user_id, poi_id) do update set collected_at = excluded.collected_at;
    insert into feed_event (user_id, type, collection_id, created_at)
    select user_id, 'POI_COLLECTED', id, collected_at from poi_collection where user_id = '5eed0000-0000-4000-8000-000000000004'::uuid and poi_id = (select id from point_of_interest where name = 'Muzeum Wsi Lubelskiej')
    on conflict do nothing;
    insert into poi_collection (user_id, poi_id, collected_at)
    values ('5eed0000-0000-4000-8000-000000000005'::uuid, (select id from point_of_interest where name = 'Plaża nad Zalewem Zemborzyckim'), now() - interval '5 days 5 hours')
    on conflict (user_id, poi_id) do update set collected_at = excluded.collected_at;
    insert into feed_event (user_id, type, collection_id, created_at)
    select user_id, 'POI_COLLECTED', id, collected_at from poi_collection where user_id = '5eed0000-0000-4000-8000-000000000005'::uuid and poi_id = (select id from point_of_interest where name = 'Plaża nad Zalewem Zemborzyckim')
    on conflict do nothing;
    insert into poi_collection (user_id, poi_id, collected_at)
    values ('5eed0000-0000-4000-8000-000000000006'::uuid, (select id from point_of_interest where name = 'Brama Grodzka'), now() - interval '2 days 5 hours')
    on conflict (user_id, poi_id) do update set collected_at = excluded.collected_at;
    insert into feed_event (user_id, type, collection_id, created_at)
    select user_id, 'POI_COLLECTED', id, collected_at from poi_collection where user_id = '5eed0000-0000-4000-8000-000000000006'::uuid and poi_id = (select id from point_of_interest where name = 'Brama Grodzka')
    on conflict do nothing;
    insert into poi_collection (user_id, poi_id, collected_at)
    values ('5eed0000-0000-4000-8000-000000000006'::uuid, (select id from point_of_interest where name = 'Lubelska Trasa Podziemna'), now() - interval '1 days 5 hours')
    on conflict (user_id, poi_id) do update set collected_at = excluded.collected_at;
    insert into feed_event (user_id, type, collection_id, created_at)
    select user_id, 'POI_COLLECTED', id, collected_at from poi_collection where user_id = '5eed0000-0000-4000-8000-000000000006'::uuid and poi_id = (select id from point_of_interest where name = 'Lubelska Trasa Podziemna')
    on conflict do nothing;
    insert into poi_collection (user_id, poi_id, collected_at)
    values ('5eed0000-0000-4000-8000-000000000007'::uuid, (select id from point_of_interest where name = 'Plaża nad Zalewem Zemborzyckim'), now() - interval '6 days 5 hours')
    on conflict (user_id, poi_id) do update set collected_at = excluded.collected_at;
    insert into feed_event (user_id, type, collection_id, created_at)
    select user_id, 'POI_COLLECTED', id, collected_at from poi_collection where user_id = '5eed0000-0000-4000-8000-000000000007'::uuid and poi_id = (select id from point_of_interest where name = 'Plaża nad Zalewem Zemborzyckim')
    on conflict do nothing;

    -- polubienia
    insert into feed_like (event_id, user_id, created_at) select (select e.id from feed_event e join route r on r.id = e.route_id where r.user_id = '5eed0000-0000-4000-8000-000000000001'::uuid and r.name = 'Bulwary i Zalew Zemborzycki'), '5eed0000-0000-4000-8000-000000000004'::uuid, now() - interval '50 minutes' on conflict do nothing;
    insert into feed_like (event_id, user_id, created_at) select (select e.id from feed_event e join route r on r.id = e.route_id where r.user_id = '5eed0000-0000-4000-8000-000000000001'::uuid and r.name = 'Bulwary i Zalew Zemborzycki'), '5eed0000-0000-4000-8000-000000000005'::uuid, now() - interval '75 minutes' on conflict do nothing;
    insert into feed_like (event_id, user_id, created_at) select (select e.id from feed_event e join route r on r.id = e.route_id where r.user_id = '5eed0000-0000-4000-8000-000000000001'::uuid and r.name = 'Wieczorny spacer po Starówce'), '5eed0000-0000-4000-8000-000000000003'::uuid, now() - interval '520 minutes' on conflict do nothing;
    insert into feed_like (event_id, user_id, created_at) select (select e.id from feed_event e join route r on r.id = e.route_id where r.user_id = '5eed0000-0000-4000-8000-000000000001'::uuid and r.name = 'Wieczorny spacer po Starówce'), '5eed0000-0000-4000-8000-000000000004'::uuid, now() - interval '220 minutes' on conflict do nothing;
    insert into feed_like (event_id, user_id, created_at) select (select e.id from feed_event e join route r on r.id = e.route_id where r.user_id = '5eed0000-0000-4000-8000-000000000001'::uuid and r.name = 'Wieczorny spacer po Starówce'), '5eed0000-0000-4000-8000-000000000006'::uuid, now() - interval '39 minutes' on conflict do nothing;
    insert into feed_like (event_id, user_id, created_at) select (select e.id from feed_event e join route r on r.id = e.route_id where r.user_id = '5eed0000-0000-4000-8000-000000000001'::uuid and r.name = 'Wieczorny spacer po Starówce'), '5eed0000-0000-4000-8000-000000000002'::uuid, now() - interval '89 minutes' on conflict do nothing;
    insert into feed_like (event_id, user_id, created_at) select (select e.id from feed_event e join route r on r.id = e.route_id where r.user_id = '5eed0000-0000-4000-8000-000000000002'::uuid and r.name = 'Szybka dycha przy Czechowie'), '5eed0000-0000-4000-8000-000000000008'::uuid, now() - interval '93 minutes' on conflict do nothing;
    insert into feed_like (event_id, user_id, created_at) select (select e.id from feed_event e join route r on r.id = e.route_id where r.user_id = '5eed0000-0000-4000-8000-000000000002'::uuid and r.name = 'Szybka dycha przy Czechowie'), '5eed0000-0000-4000-8000-000000000001'::uuid, now() - interval '565 minutes' on conflict do nothing;
    insert into feed_like (event_id, user_id, created_at) select (select e.id from feed_event e join route r on r.id = e.route_id where r.user_id = '5eed0000-0000-4000-8000-000000000002'::uuid and r.name = 'Szybka dycha przy Czechowie'), '5eed0000-0000-4000-8000-000000000003'::uuid, now() - interval '435 minutes' on conflict do nothing;
    insert into feed_like (event_id, user_id, created_at) select (select e.id from feed_event e join route r on r.id = e.route_id where r.user_id = '5eed0000-0000-4000-8000-000000000003'::uuid and r.name = 'Las Dąbrowa - leśna pętla'), '5eed0000-0000-4000-8000-000000000002'::uuid, now() - interval '64 minutes' on conflict do nothing;
    insert into feed_like (event_id, user_id, created_at) select (select e.id from feed_event e join route r on r.id = e.route_id where r.user_id = '5eed0000-0000-4000-8000-000000000003'::uuid and r.name = 'Las Dąbrowa - leśna pętla'), me, now() - interval '591 minutes' on conflict do nothing;
    insert into feed_like (event_id, user_id, created_at) select (select e.id from feed_event e join route r on r.id = e.route_id where r.user_id = '5eed0000-0000-4000-8000-000000000003'::uuid and r.name = 'Las Dąbrowa - leśna pętla'), '5eed0000-0000-4000-8000-000000000007'::uuid, now() - interval '600 minutes' on conflict do nothing;
    insert into feed_like (event_id, user_id, created_at) select (select e.id from feed_event e join route r on r.id = e.route_id where r.user_id = '5eed0000-0000-4000-8000-000000000003'::uuid and r.name = 'Las Dąbrowa - leśna pętla'), '5eed0000-0000-4000-8000-000000000006'::uuid, now() - interval '407 minutes' on conflict do nothing;
    insert into feed_like (event_id, user_id, created_at) select (select e.id from feed_event e join route r on r.id = e.route_id where r.user_id = '5eed0000-0000-4000-8000-000000000005'::uuid and r.name = 'Poranny bieg wokół kampusu'), '5eed0000-0000-4000-8000-000000000001'::uuid, now() - interval '571 minutes' on conflict do nothing;
    insert into feed_like (event_id, user_id, created_at) select (select e.id from feed_event e join route r on r.id = e.route_id where r.user_id = '5eed0000-0000-4000-8000-000000000006'::uuid and r.name = 'Spacer nad Czechówką'), '5eed0000-0000-4000-8000-000000000005'::uuid, now() - interval '430 minutes' on conflict do nothing;
    insert into feed_like (event_id, user_id, created_at) select (select e.id from feed_event e join route r on r.id = e.route_id where r.user_id = '5eed0000-0000-4000-8000-000000000007'::uuid and r.name = 'Interwały nad Zalewem'), '5eed0000-0000-4000-8000-000000000002'::uuid, now() - interval '585 minutes' on conflict do nothing;
    insert into feed_like (event_id, user_id, created_at) select (select e.id from feed_event e join activity a on a.id = e.activity_id where a.user_id = '5eed0000-0000-4000-8000-000000000001'::uuid and a.client_id = '5eed0001-0000-4000-8000-00000000c11d'), '5eed0000-0000-4000-8000-000000000004'::uuid, now() - interval '596 minutes' on conflict do nothing;
    insert into feed_like (event_id, user_id, created_at) select (select e.id from feed_event e join activity a on a.id = e.activity_id where a.user_id = '5eed0000-0000-4000-8000-000000000001'::uuid and a.client_id = '5eed0001-0000-4000-8000-00000000c11d'), '5eed0000-0000-4000-8000-000000000002'::uuid, now() - interval '585 minutes' on conflict do nothing;
    insert into feed_like (event_id, user_id, created_at) select (select e.id from feed_event e join activity a on a.id = e.activity_id where a.user_id = '5eed0000-0000-4000-8000-000000000001'::uuid and a.client_id = '5eed0002-0000-4000-8000-00000000c11d'), '5eed0000-0000-4000-8000-000000000005'::uuid, now() - interval '578 minutes' on conflict do nothing;
    insert into feed_like (event_id, user_id, created_at) select (select e.id from feed_event e join activity a on a.id = e.activity_id where a.user_id = '5eed0000-0000-4000-8000-000000000001'::uuid and a.client_id = '5eed0002-0000-4000-8000-00000000c11d'), '5eed0000-0000-4000-8000-000000000004'::uuid, now() - interval '62 minutes' on conflict do nothing;
    insert into feed_like (event_id, user_id, created_at) select (select e.id from feed_event e join activity a on a.id = e.activity_id where a.user_id = '5eed0000-0000-4000-8000-000000000001'::uuid and a.client_id = '5eed0002-0000-4000-8000-00000000c11d'), '5eed0000-0000-4000-8000-000000000002'::uuid, now() - interval '211 minutes' on conflict do nothing;
    insert into feed_like (event_id, user_id, created_at) select (select e.id from feed_event e join activity a on a.id = e.activity_id where a.user_id = '5eed0000-0000-4000-8000-000000000001'::uuid and a.client_id = '5eed0002-0000-4000-8000-00000000c11d'), '5eed0000-0000-4000-8000-000000000006'::uuid, now() - interval '509 minutes' on conflict do nothing;
    insert into feed_like (event_id, user_id, created_at) select (select e.id from feed_event e join activity a on a.id = e.activity_id where a.user_id = '5eed0000-0000-4000-8000-000000000001'::uuid and a.client_id = '5eed0002-0000-4000-8000-00000000c11d'), '5eed0000-0000-4000-8000-000000000007'::uuid, now() - interval '545 minutes' on conflict do nothing;
    insert into feed_like (event_id, user_id, created_at) select (select e.id from feed_event e join activity a on a.id = e.activity_id where a.user_id = '5eed0000-0000-4000-8000-000000000002'::uuid and a.client_id = '5eed0004-0000-4000-8000-00000000c11d'), '5eed0000-0000-4000-8000-000000000007'::uuid, now() - interval '465 minutes' on conflict do nothing;
    insert into feed_like (event_id, user_id, created_at) select (select e.id from feed_event e join activity a on a.id = e.activity_id where a.user_id = '5eed0000-0000-4000-8000-000000000002'::uuid and a.client_id = '5eed0004-0000-4000-8000-00000000c11d'), '5eed0000-0000-4000-8000-000000000005'::uuid, now() - interval '371 minutes' on conflict do nothing;
    insert into feed_like (event_id, user_id, created_at) select (select e.id from feed_event e join activity a on a.id = e.activity_id where a.user_id = '5eed0000-0000-4000-8000-000000000002'::uuid and a.client_id = '5eed0004-0000-4000-8000-00000000c11d'), '5eed0000-0000-4000-8000-000000000006'::uuid, now() - interval '307 minutes' on conflict do nothing;
    insert into feed_like (event_id, user_id, created_at) select (select e.id from feed_event e join activity a on a.id = e.activity_id where a.user_id = '5eed0000-0000-4000-8000-000000000002'::uuid and a.client_id = '5eed0005-0000-4000-8000-00000000c11d'), '5eed0000-0000-4000-8000-000000000004'::uuid, now() - interval '250 minutes' on conflict do nothing;
    insert into feed_like (event_id, user_id, created_at) select (select e.id from feed_event e join activity a on a.id = e.activity_id where a.user_id = '5eed0000-0000-4000-8000-000000000003'::uuid and a.client_id = '5eed0008-0000-4000-8000-00000000c11d'), '5eed0000-0000-4000-8000-000000000006'::uuid, now() - interval '460 minutes' on conflict do nothing;
    insert into feed_like (event_id, user_id, created_at) select (select e.id from feed_event e join activity a on a.id = e.activity_id where a.user_id = '5eed0000-0000-4000-8000-000000000003'::uuid and a.client_id = '5eed0008-0000-4000-8000-00000000c11d'), me, now() - interval '295 minutes' on conflict do nothing;
    insert into feed_like (event_id, user_id, created_at) select (select e.id from feed_event e join activity a on a.id = e.activity_id where a.user_id = '5eed0000-0000-4000-8000-000000000003'::uuid and a.client_id = '5eed0008-0000-4000-8000-00000000c11d'), '5eed0000-0000-4000-8000-000000000005'::uuid, now() - interval '75 minutes' on conflict do nothing;
    insert into feed_like (event_id, user_id, created_at) select (select e.id from feed_event e join activity a on a.id = e.activity_id where a.user_id = '5eed0000-0000-4000-8000-000000000003'::uuid and a.client_id = '5eed0008-0000-4000-8000-00000000c11d'), '5eed0000-0000-4000-8000-000000000004'::uuid, now() - interval '121 minutes' on conflict do nothing;
    insert into feed_like (event_id, user_id, created_at) select (select e.id from feed_event e join activity a on a.id = e.activity_id where a.user_id = '5eed0000-0000-4000-8000-000000000004'::uuid and a.client_id = '5eed0009-0000-4000-8000-00000000c11d'), '5eed0000-0000-4000-8000-000000000008'::uuid, now() - interval '501 minutes' on conflict do nothing;
    insert into feed_like (event_id, user_id, created_at) select (select e.id from feed_event e join activity a on a.id = e.activity_id where a.user_id = '5eed0000-0000-4000-8000-000000000004'::uuid and a.client_id = '5eed0009-0000-4000-8000-00000000c11d'), '5eed0000-0000-4000-8000-000000000002'::uuid, now() - interval '432 minutes' on conflict do nothing;
    insert into feed_like (event_id, user_id, created_at) select (select e.id from feed_event e join activity a on a.id = e.activity_id where a.user_id = '5eed0000-0000-4000-8000-000000000004'::uuid and a.client_id = '5eed0009-0000-4000-8000-00000000c11d'), '5eed0000-0000-4000-8000-000000000003'::uuid, now() - interval '41 minutes' on conflict do nothing;
    insert into feed_like (event_id, user_id, created_at) select (select e.id from feed_event e join activity a on a.id = e.activity_id where a.user_id = '5eed0000-0000-4000-8000-000000000004'::uuid and a.client_id = '5eed0009-0000-4000-8000-00000000c11d'), me, now() - interval '80 minutes' on conflict do nothing;
    insert into feed_like (event_id, user_id, created_at) select (select e.id from feed_event e join activity a on a.id = e.activity_id where a.user_id = '5eed0000-0000-4000-8000-000000000006'::uuid and a.client_id = '5eed0011-0000-4000-8000-00000000c11d'), '5eed0000-0000-4000-8000-000000000007'::uuid, now() - interval '509 minutes' on conflict do nothing;
    insert into feed_like (event_id, user_id, created_at) select (select e.id from feed_event e join activity a on a.id = e.activity_id where a.user_id = '5eed0000-0000-4000-8000-000000000006'::uuid and a.client_id = '5eed0011-0000-4000-8000-00000000c11d'), '5eed0000-0000-4000-8000-000000000003'::uuid, now() - interval '594 minutes' on conflict do nothing;
    insert into feed_like (event_id, user_id, created_at) select (select e.id from feed_event e join activity a on a.id = e.activity_id where a.user_id = '5eed0000-0000-4000-8000-000000000006'::uuid and a.client_id = '5eed0011-0000-4000-8000-00000000c11d'), me, now() - interval '468 minutes' on conflict do nothing;
    insert into feed_like (event_id, user_id, created_at) select (select e.id from feed_event e join activity a on a.id = e.activity_id where a.user_id = '5eed0000-0000-4000-8000-000000000006'::uuid and a.client_id = '5eed0011-0000-4000-8000-00000000c11d'), '5eed0000-0000-4000-8000-000000000008'::uuid, now() - interval '71 minutes' on conflict do nothing;
    insert into feed_like (event_id, user_id, created_at) select (select e.id from feed_event e join activity a on a.id = e.activity_id where a.user_id = me and a.client_id = '5eed0013-0000-4000-8000-00000000c11d'), '5eed0000-0000-4000-8000-000000000008'::uuid, now() - interval '67 minutes' on conflict do nothing;
    insert into feed_like (event_id, user_id, created_at) select (select e.id from feed_event e join activity a on a.id = e.activity_id where a.user_id = me and a.client_id = '5eed0013-0000-4000-8000-00000000c11d'), '5eed0000-0000-4000-8000-000000000006'::uuid, now() - interval '63 minutes' on conflict do nothing;
    insert into feed_like (event_id, user_id, created_at) select (select e.id from feed_event e join activity a on a.id = e.activity_id where a.user_id = me and a.client_id = '5eed0015-0000-4000-8000-00000000c11d'), '5eed0000-0000-4000-8000-000000000005'::uuid, now() - interval '396 minutes' on conflict do nothing;
    insert into feed_like (event_id, user_id, created_at) select (select e.id from feed_event e join activity a on a.id = e.activity_id where a.user_id = me and a.client_id = '5eed0015-0000-4000-8000-00000000c11d'), '5eed0000-0000-4000-8000-000000000006'::uuid, now() - interval '356 minutes' on conflict do nothing;
    insert into feed_like (event_id, user_id, created_at) select (select e.id from feed_event e join activity a on a.id = e.activity_id where a.user_id = me and a.client_id = '5eed0015-0000-4000-8000-00000000c11d'), '5eed0000-0000-4000-8000-000000000008'::uuid, now() - interval '24 minutes' on conflict do nothing;
    insert into feed_like (event_id, user_id, created_at) select (select e.id from feed_event e join activity a on a.id = e.activity_id where a.user_id = me and a.client_id = '5eed0015-0000-4000-8000-00000000c11d'), '5eed0000-0000-4000-8000-000000000004'::uuid, now() - interval '473 minutes' on conflict do nothing;
    insert into feed_like (event_id, user_id, created_at) select (select e.id from feed_event e join activity a on a.id = e.activity_id where a.user_id = me and a.client_id = '5eed0015-0000-4000-8000-00000000c11d'), '5eed0000-0000-4000-8000-000000000003'::uuid, now() - interval '364 minutes' on conflict do nothing;
    insert into feed_like (event_id, user_id, created_at) select (select e.id from feed_event e join review rv on rv.id = e.review_id join route r on r.id = rv.route_id where rv.user_id = '5eed0000-0000-4000-8000-000000000003'::uuid and r.user_id = '5eed0000-0000-4000-8000-000000000001'::uuid and r.name = 'Wieczorny spacer po Starówce'), '5eed0000-0000-4000-8000-000000000002'::uuid, now() - interval '506 minutes' on conflict do nothing;
    insert into feed_like (event_id, user_id, created_at) select (select e.id from feed_event e join review rv on rv.id = e.review_id join route r on r.id = rv.route_id where rv.user_id = '5eed0000-0000-4000-8000-000000000006'::uuid and r.user_id = '5eed0000-0000-4000-8000-000000000001'::uuid and r.name = 'Wieczorny spacer po Starówce'), '5eed0000-0000-4000-8000-000000000005'::uuid, now() - interval '133 minutes' on conflict do nothing;
    insert into feed_like (event_id, user_id, created_at) select (select e.id from feed_event e join review rv on rv.id = e.review_id join route r on r.id = rv.route_id where rv.user_id = '5eed0000-0000-4000-8000-000000000002'::uuid and r.user_id = '5eed0000-0000-4000-8000-000000000001'::uuid and r.name = 'Bulwary i Zalew Zemborzycki'), '5eed0000-0000-4000-8000-000000000005'::uuid, now() - interval '171 minutes' on conflict do nothing;
    insert into feed_like (event_id, user_id, created_at) select (select e.id from feed_event e join review rv on rv.id = e.review_id join route r on r.id = rv.route_id where rv.user_id = '5eed0000-0000-4000-8000-000000000002'::uuid and r.user_id = '5eed0000-0000-4000-8000-000000000001'::uuid and r.name = 'Bulwary i Zalew Zemborzycki'), me, now() - interval '460 minutes' on conflict do nothing;
    insert into feed_like (event_id, user_id, created_at) select (select e.id from feed_event e join review rv on rv.id = e.review_id join route r on r.id = rv.route_id where rv.user_id = '5eed0000-0000-4000-8000-000000000002'::uuid and r.user_id = '5eed0000-0000-4000-8000-000000000001'::uuid and r.name = 'Bulwary i Zalew Zemborzycki'), '5eed0000-0000-4000-8000-000000000008'::uuid, now() - interval '412 minutes' on conflict do nothing;
    insert into feed_like (event_id, user_id, created_at) select (select e.id from feed_event e join review rv on rv.id = e.review_id join route r on r.id = rv.route_id where rv.user_id = '5eed0000-0000-4000-8000-000000000002'::uuid and r.user_id = '5eed0000-0000-4000-8000-000000000001'::uuid and r.name = 'Bulwary i Zalew Zemborzycki'), '5eed0000-0000-4000-8000-000000000007'::uuid, now() - interval '563 minutes' on conflict do nothing;
    insert into feed_like (event_id, user_id, created_at) select (select e.id from feed_event e join review rv on rv.id = e.review_id join route r on r.id = rv.route_id where rv.user_id = '5eed0000-0000-4000-8000-000000000002'::uuid and r.user_id = '5eed0000-0000-4000-8000-000000000001'::uuid and r.name = 'Bulwary i Zalew Zemborzycki'), '5eed0000-0000-4000-8000-000000000001'::uuid, now() - interval '285 minutes' on conflict do nothing;
    insert into feed_like (event_id, user_id, created_at) select (select e.id from feed_event e join review rv on rv.id = e.review_id join route r on r.id = rv.route_id where rv.user_id = '5eed0000-0000-4000-8000-000000000004'::uuid and r.user_id = '5eed0000-0000-4000-8000-000000000001'::uuid and r.name = 'Bulwary i Zalew Zemborzycki'), '5eed0000-0000-4000-8000-000000000008'::uuid, now() - interval '564 minutes' on conflict do nothing;
    insert into feed_like (event_id, user_id, created_at) select (select e.id from feed_event e join review rv on rv.id = e.review_id join route r on r.id = rv.route_id where rv.user_id = '5eed0000-0000-4000-8000-000000000007'::uuid and r.user_id = '5eed0000-0000-4000-8000-000000000003'::uuid and r.name = 'Las Dąbrowa - leśna pętla'), '5eed0000-0000-4000-8000-000000000008'::uuid, now() - interval '390 minutes' on conflict do nothing;
    insert into feed_like (event_id, user_id, created_at) select (select e.id from feed_event e join review rv on rv.id = e.review_id join route r on r.id = rv.route_id where rv.user_id = '5eed0000-0000-4000-8000-000000000007'::uuid and r.user_id = '5eed0000-0000-4000-8000-000000000003'::uuid and r.name = 'Las Dąbrowa - leśna pętla'), '5eed0000-0000-4000-8000-000000000003'::uuid, now() - interval '237 minutes' on conflict do nothing;
    insert into feed_like (event_id, user_id, created_at) select (select e.id from feed_event e join review rv on rv.id = e.review_id join route r on r.id = rv.route_id where rv.user_id = me and r.user_id = '5eed0000-0000-4000-8000-000000000003'::uuid and r.name = 'Las Dąbrowa - leśna pętla'), '5eed0000-0000-4000-8000-000000000002'::uuid, now() - interval '181 minutes' on conflict do nothing;
    insert into feed_like (event_id, user_id, created_at) select (select e.id from feed_event e join review rv on rv.id = e.review_id join route r on r.id = rv.route_id where rv.user_id = '5eed0000-0000-4000-8000-000000000002'::uuid and r.user_id = '5eed0000-0000-4000-8000-000000000004'::uuid and r.name = 'Lublin - Kozłówka'), '5eed0000-0000-4000-8000-000000000005'::uuid, now() - interval '239 minutes' on conflict do nothing;
    insert into feed_like (event_id, user_id, created_at) select (select e.id from feed_event e join review rv on rv.id = e.review_id join route r on r.id = rv.route_id where rv.user_id = '5eed0000-0000-4000-8000-000000000001'::uuid and r.user_id = '5eed0000-0000-4000-8000-000000000002'::uuid and r.name = 'Szybka dycha przy Czechowie'), '5eed0000-0000-4000-8000-000000000004'::uuid, now() - interval '5 minutes' on conflict do nothing;
    insert into feed_like (event_id, user_id, created_at) select (select e.id from feed_event e join review rv on rv.id = e.review_id join route r on r.id = rv.route_id where rv.user_id = '5eed0000-0000-4000-8000-000000000001'::uuid and r.user_id = '5eed0000-0000-4000-8000-000000000002'::uuid and r.name = 'Szybka dycha przy Czechowie'), me, now() - interval '150 minutes' on conflict do nothing;
    insert into feed_like (event_id, user_id, created_at) select (select e.id from feed_event e join review rv on rv.id = e.review_id join route r on r.id = rv.route_id where rv.user_id = '5eed0000-0000-4000-8000-000000000001'::uuid and r.user_id = '5eed0000-0000-4000-8000-000000000002'::uuid and r.name = 'Szybka dycha przy Czechowie'), '5eed0000-0000-4000-8000-000000000008'::uuid, now() - interval '430 minutes' on conflict do nothing;
    insert into feed_like (event_id, user_id, created_at) select (select e.id from feed_event e join poi_collection pc on pc.id = e.collection_id join point_of_interest p on p.id = pc.poi_id where pc.user_id = me and p.name = 'Brama Krakowska'), '5eed0000-0000-4000-8000-000000000006'::uuid, now() - interval '129 minutes' on conflict do nothing;
    insert into feed_like (event_id, user_id, created_at) select (select e.id from feed_event e join poi_collection pc on pc.id = e.collection_id join point_of_interest p on p.id = pc.poi_id where pc.user_id = me and p.name = 'Brama Krakowska'), '5eed0000-0000-4000-8000-000000000005'::uuid, now() - interval '528 minutes' on conflict do nothing;
    insert into feed_like (event_id, user_id, created_at) select (select e.id from feed_event e join poi_collection pc on pc.id = e.collection_id join point_of_interest p on p.id = pc.poi_id where pc.user_id = me and p.name = 'Brama Krakowska'), '5eed0000-0000-4000-8000-000000000007'::uuid, now() - interval '56 minutes' on conflict do nothing;
    insert into feed_like (event_id, user_id, created_at) select (select e.id from feed_event e join poi_collection pc on pc.id = e.collection_id join point_of_interest p on p.id = pc.poi_id where pc.user_id = me and p.name = 'Brama Krakowska'), '5eed0000-0000-4000-8000-000000000003'::uuid, now() - interval '468 minutes' on conflict do nothing;
    insert into feed_like (event_id, user_id, created_at) select (select e.id from feed_event e join poi_collection pc on pc.id = e.collection_id join point_of_interest p on p.id = pc.poi_id where pc.user_id = me and p.name = 'Zamek Lubelski'), '5eed0000-0000-4000-8000-000000000007'::uuid, now() - interval '494 minutes' on conflict do nothing;
    insert into feed_like (event_id, user_id, created_at) select (select e.id from feed_event e join poi_collection pc on pc.id = e.collection_id join point_of_interest p on p.id = pc.poi_id where pc.user_id = me and p.name = 'Zamek Lubelski'), '5eed0000-0000-4000-8000-000000000004'::uuid, now() - interval '411 minutes' on conflict do nothing;
    insert into feed_like (event_id, user_id, created_at) select (select e.id from feed_event e join poi_collection pc on pc.id = e.collection_id join point_of_interest p on p.id = pc.poi_id where pc.user_id = me and p.name = 'Zamek Lubelski'), '5eed0000-0000-4000-8000-000000000008'::uuid, now() - interval '64 minutes' on conflict do nothing;
    insert into feed_like (event_id, user_id, created_at) select (select e.id from feed_event e join poi_collection pc on pc.id = e.collection_id join point_of_interest p on p.id = pc.poi_id where pc.user_id = me and p.name = 'Zamek Lubelski'), '5eed0000-0000-4000-8000-000000000006'::uuid, now() - interval '196 minutes' on conflict do nothing;
    insert into feed_like (event_id, user_id, created_at) select (select e.id from feed_event e join poi_collection pc on pc.id = e.collection_id join point_of_interest p on p.id = pc.poi_id where pc.user_id = me and p.name = 'Zamek Lubelski'), '5eed0000-0000-4000-8000-000000000001'::uuid, now() - interval '69 minutes' on conflict do nothing;
    insert into feed_like (event_id, user_id, created_at) select (select e.id from feed_event e join poi_collection pc on pc.id = e.collection_id join point_of_interest p on p.id = pc.poi_id where pc.user_id = '5eed0000-0000-4000-8000-000000000001'::uuid and p.name = 'Brama Krakowska'), me, now() - interval '167 minutes' on conflict do nothing;
    insert into feed_like (event_id, user_id, created_at) select (select e.id from feed_event e join poi_collection pc on pc.id = e.collection_id join point_of_interest p on p.id = pc.poi_id where pc.user_id = '5eed0000-0000-4000-8000-000000000001'::uuid and p.name = 'Plac Litewski'), '5eed0000-0000-4000-8000-000000000002'::uuid, now() - interval '1 minutes' on conflict do nothing;
    insert into feed_like (event_id, user_id, created_at) select (select e.id from feed_event e join poi_collection pc on pc.id = e.collection_id join point_of_interest p on p.id = pc.poi_id where pc.user_id = '5eed0000-0000-4000-8000-000000000001'::uuid and p.name = 'Plac Litewski'), me, now() - interval '581 minutes' on conflict do nothing;
    insert into feed_like (event_id, user_id, created_at) select (select e.id from feed_event e join poi_collection pc on pc.id = e.collection_id join point_of_interest p on p.id = pc.poi_id where pc.user_id = '5eed0000-0000-4000-8000-000000000001'::uuid and p.name = 'Ogród Saski'), '5eed0000-0000-4000-8000-000000000003'::uuid, now() - interval '373 minutes' on conflict do nothing;
    insert into feed_like (event_id, user_id, created_at) select (select e.id from feed_event e join poi_collection pc on pc.id = e.collection_id join point_of_interest p on p.id = pc.poi_id where pc.user_id = '5eed0000-0000-4000-8000-000000000002'::uuid and p.name = 'Zamek Lubelski'), '5eed0000-0000-4000-8000-000000000001'::uuid, now() - interval '386 minutes' on conflict do nothing;
    insert into feed_like (event_id, user_id, created_at) select (select e.id from feed_event e join poi_collection pc on pc.id = e.collection_id join point_of_interest p on p.id = pc.poi_id where pc.user_id = '5eed0000-0000-4000-8000-000000000002'::uuid and p.name = 'Zamek Lubelski'), me, now() - interval '153 minutes' on conflict do nothing;
    insert into feed_like (event_id, user_id, created_at) select (select e.id from feed_event e join poi_collection pc on pc.id = e.collection_id join point_of_interest p on p.id = pc.poi_id where pc.user_id = '5eed0000-0000-4000-8000-000000000002'::uuid and p.name = 'Zamek Lubelski'), '5eed0000-0000-4000-8000-000000000003'::uuid, now() - interval '259 minutes' on conflict do nothing;
    insert into feed_like (event_id, user_id, created_at) select (select e.id from feed_event e join poi_collection pc on pc.id = e.collection_id join point_of_interest p on p.id = pc.poi_id where pc.user_id = '5eed0000-0000-4000-8000-000000000002'::uuid and p.name = 'Zamek Lubelski'), '5eed0000-0000-4000-8000-000000000006'::uuid, now() - interval '356 minutes' on conflict do nothing;
    insert into feed_like (event_id, user_id, created_at) select (select e.id from feed_event e join poi_collection pc on pc.id = e.collection_id join point_of_interest p on p.id = pc.poi_id where pc.user_id = '5eed0000-0000-4000-8000-000000000002'::uuid and p.name = 'Górki Czechowskie - punkt widokowy'), '5eed0000-0000-4000-8000-000000000007'::uuid, now() - interval '500 minutes' on conflict do nothing;
    insert into feed_like (event_id, user_id, created_at) select (select e.id from feed_event e join poi_collection pc on pc.id = e.collection_id join point_of_interest p on p.id = pc.poi_id where pc.user_id = '5eed0000-0000-4000-8000-000000000002'::uuid and p.name = 'Górki Czechowskie - punkt widokowy'), '5eed0000-0000-4000-8000-000000000005'::uuid, now() - interval '478 minutes' on conflict do nothing;
    insert into feed_like (event_id, user_id, created_at) select (select e.id from feed_event e join poi_collection pc on pc.id = e.collection_id join point_of_interest p on p.id = pc.poi_id where pc.user_id = '5eed0000-0000-4000-8000-000000000002'::uuid and p.name = 'Górki Czechowskie - punkt widokowy'), '5eed0000-0000-4000-8000-000000000001'::uuid, now() - interval '492 minutes' on conflict do nothing;
    insert into feed_like (event_id, user_id, created_at) select (select e.id from feed_event e join poi_collection pc on pc.id = e.collection_id join point_of_interest p on p.id = pc.poi_id where pc.user_id = '5eed0000-0000-4000-8000-000000000002'::uuid and p.name = 'Górki Czechowskie - punkt widokowy'), me, now() - interval '496 minutes' on conflict do nothing;
    insert into feed_like (event_id, user_id, created_at) select (select e.id from feed_event e join poi_collection pc on pc.id = e.collection_id join point_of_interest p on p.id = pc.poi_id where pc.user_id = '5eed0000-0000-4000-8000-000000000003'::uuid and p.name = 'Ogród Botaniczny UMCS'), '5eed0000-0000-4000-8000-000000000002'::uuid, now() - interval '105 minutes' on conflict do nothing;
    insert into feed_like (event_id, user_id, created_at) select (select e.id from feed_event e join poi_collection pc on pc.id = e.collection_id join point_of_interest p on p.id = pc.poi_id where pc.user_id = '5eed0000-0000-4000-8000-000000000003'::uuid and p.name = 'Ogród Botaniczny UMCS'), me, now() - interval '351 minutes' on conflict do nothing;
    insert into feed_like (event_id, user_id, created_at) select (select e.id from feed_event e join poi_collection pc on pc.id = e.collection_id join point_of_interest p on p.id = pc.poi_id where pc.user_id = '5eed0000-0000-4000-8000-000000000003'::uuid and p.name = 'Rezerwat Stary Gaj'), '5eed0000-0000-4000-8000-000000000006'::uuid, now() - interval '211 minutes' on conflict do nothing;
    insert into feed_like (event_id, user_id, created_at) select (select e.id from feed_event e join poi_collection pc on pc.id = e.collection_id join point_of_interest p on p.id = pc.poi_id where pc.user_id = '5eed0000-0000-4000-8000-000000000003'::uuid and p.name = 'Rezerwat Stary Gaj'), '5eed0000-0000-4000-8000-000000000005'::uuid, now() - interval '541 minutes' on conflict do nothing;
    insert into feed_like (event_id, user_id, created_at) select (select e.id from feed_event e join poi_collection pc on pc.id = e.collection_id join point_of_interest p on p.id = pc.poi_id where pc.user_id = '5eed0000-0000-4000-8000-000000000003'::uuid and p.name = 'Rezerwat Stary Gaj'), '5eed0000-0000-4000-8000-000000000007'::uuid, now() - interval '371 minutes' on conflict do nothing;
    insert into feed_like (event_id, user_id, created_at) select (select e.id from feed_event e join poi_collection pc on pc.id = e.collection_id join point_of_interest p on p.id = pc.poi_id where pc.user_id = '5eed0000-0000-4000-8000-000000000003'::uuid and p.name = 'Rezerwat Stary Gaj'), '5eed0000-0000-4000-8000-000000000002'::uuid, now() - interval '151 minutes' on conflict do nothing;
    insert into feed_like (event_id, user_id, created_at) select (select e.id from feed_event e join poi_collection pc on pc.id = e.collection_id join point_of_interest p on p.id = pc.poi_id where pc.user_id = '5eed0000-0000-4000-8000-000000000003'::uuid and p.name = 'Rezerwat Stary Gaj'), '5eed0000-0000-4000-8000-000000000001'::uuid, now() - interval '557 minutes' on conflict do nothing;
    insert into feed_like (event_id, user_id, created_at) select (select e.id from feed_event e join poi_collection pc on pc.id = e.collection_id join point_of_interest p on p.id = pc.poi_id where pc.user_id = '5eed0000-0000-4000-8000-000000000004'::uuid and p.name = 'Muzeum Wsi Lubelskiej'), '5eed0000-0000-4000-8000-000000000006'::uuid, now() - interval '531 minutes' on conflict do nothing;
    insert into feed_like (event_id, user_id, created_at) select (select e.id from feed_event e join poi_collection pc on pc.id = e.collection_id join point_of_interest p on p.id = pc.poi_id where pc.user_id = '5eed0000-0000-4000-8000-000000000004'::uuid and p.name = 'Muzeum Wsi Lubelskiej'), '5eed0000-0000-4000-8000-000000000007'::uuid, now() - interval '376 minutes' on conflict do nothing;
    insert into feed_like (event_id, user_id, created_at) select (select e.id from feed_event e join poi_collection pc on pc.id = e.collection_id join point_of_interest p on p.id = pc.poi_id where pc.user_id = '5eed0000-0000-4000-8000-000000000004'::uuid and p.name = 'Muzeum Wsi Lubelskiej'), '5eed0000-0000-4000-8000-000000000001'::uuid, now() - interval '172 minutes' on conflict do nothing;
    insert into feed_like (event_id, user_id, created_at) select (select e.id from feed_event e join poi_collection pc on pc.id = e.collection_id join point_of_interest p on p.id = pc.poi_id where pc.user_id = '5eed0000-0000-4000-8000-000000000004'::uuid and p.name = 'Muzeum Wsi Lubelskiej'), '5eed0000-0000-4000-8000-000000000003'::uuid, now() - interval '365 minutes' on conflict do nothing;
    insert into feed_like (event_id, user_id, created_at) select (select e.id from feed_event e join poi_collection pc on pc.id = e.collection_id join point_of_interest p on p.id = pc.poi_id where pc.user_id = '5eed0000-0000-4000-8000-000000000005'::uuid and p.name = 'Plaża nad Zalewem Zemborzyckim'), '5eed0000-0000-4000-8000-000000000007'::uuid, now() - interval '229 minutes' on conflict do nothing;
    insert into feed_like (event_id, user_id, created_at) select (select e.id from feed_event e join poi_collection pc on pc.id = e.collection_id join point_of_interest p on p.id = pc.poi_id where pc.user_id = '5eed0000-0000-4000-8000-000000000006'::uuid and p.name = 'Brama Grodzka'), '5eed0000-0000-4000-8000-000000000004'::uuid, now() - interval '233 minutes' on conflict do nothing;
    insert into feed_like (event_id, user_id, created_at) select (select e.id from feed_event e join poi_collection pc on pc.id = e.collection_id join point_of_interest p on p.id = pc.poi_id where pc.user_id = '5eed0000-0000-4000-8000-000000000006'::uuid and p.name = 'Brama Grodzka'), '5eed0000-0000-4000-8000-000000000008'::uuid, now() - interval '205 minutes' on conflict do nothing;
    insert into feed_like (event_id, user_id, created_at) select (select e.id from feed_event e join poi_collection pc on pc.id = e.collection_id join point_of_interest p on p.id = pc.poi_id where pc.user_id = '5eed0000-0000-4000-8000-000000000006'::uuid and p.name = 'Brama Grodzka'), '5eed0000-0000-4000-8000-000000000002'::uuid, now() - interval '531 minutes' on conflict do nothing;
    insert into feed_like (event_id, user_id, created_at) select (select e.id from feed_event e join poi_collection pc on pc.id = e.collection_id join point_of_interest p on p.id = pc.poi_id where pc.user_id = '5eed0000-0000-4000-8000-000000000006'::uuid and p.name = 'Brama Grodzka'), me, now() - interval '505 minutes' on conflict do nothing;
    insert into feed_like (event_id, user_id, created_at) select (select e.id from feed_event e join poi_collection pc on pc.id = e.collection_id join point_of_interest p on p.id = pc.poi_id where pc.user_id = '5eed0000-0000-4000-8000-000000000006'::uuid and p.name = 'Lubelska Trasa Podziemna'), '5eed0000-0000-4000-8000-000000000001'::uuid, now() - interval '287 minutes' on conflict do nothing;
    insert into feed_like (event_id, user_id, created_at) select (select e.id from feed_event e join poi_collection pc on pc.id = e.collection_id join point_of_interest p on p.id = pc.poi_id where pc.user_id = '5eed0000-0000-4000-8000-000000000006'::uuid and p.name = 'Lubelska Trasa Podziemna'), me, now() - interval '484 minutes' on conflict do nothing;
    insert into feed_like (event_id, user_id, created_at) select (select e.id from feed_event e join poi_collection pc on pc.id = e.collection_id join point_of_interest p on p.id = pc.poi_id where pc.user_id = '5eed0000-0000-4000-8000-000000000007'::uuid and p.name = 'Plaża nad Zalewem Zemborzyckim'), '5eed0000-0000-4000-8000-000000000004'::uuid, now() - interval '353 minutes' on conflict do nothing;
    insert into feed_like (event_id, user_id, created_at) select (select e.id from feed_event e join poi_collection pc on pc.id = e.collection_id join point_of_interest p on p.id = pc.poi_id where pc.user_id = '5eed0000-0000-4000-8000-000000000007'::uuid and p.name = 'Plaża nad Zalewem Zemborzyckim'), '5eed0000-0000-4000-8000-000000000006'::uuid, now() - interval '458 minutes' on conflict do nothing;

    -- komentarze (odpowiedzi z oznaczeniem osoby)
    insert into feed_comment (event_id, user_id, reply_to_user_id, body, created_at)
    select e.id, '5eed0000-0000-4000-8000-000000000003'::uuid, null, 'Byłam wczoraj, rzeczywiście pięknie!', e.created_at + interval '90 minutes'
    from feed_event e where e.id = (select e.id from feed_event e join route r on r.id = e.route_id where r.user_id = '5eed0000-0000-4000-8000-000000000001'::uuid and r.name = 'Wieczorny spacer po Starówce');
    insert into feed_comment (event_id, user_id, reply_to_user_id, body, created_at)
    select e.id, '5eed0000-0000-4000-8000-000000000001'::uuid, '5eed0000-0000-4000-8000-000000000003'::uuid, 'Cieszę się, że się podobało :)', e.created_at + interval '140 minutes'
    from feed_event e where e.id = (select e.id from feed_event e join route r on r.id = e.route_id where r.user_id = '5eed0000-0000-4000-8000-000000000001'::uuid and r.name = 'Wieczorny spacer po Starówce');
    insert into feed_comment (event_id, user_id, reply_to_user_id, body, created_at)
    select e.id, me, null, 'A ile to zajęło w spokojnym tempie?', e.created_at + interval '300 minutes'
    from feed_event e where e.id = (select e.id from feed_event e join route r on r.id = e.route_id where r.user_id = '5eed0000-0000-4000-8000-000000000001'::uuid and r.name = 'Wieczorny spacer po Starówce');
    insert into feed_comment (event_id, user_id, reply_to_user_id, body, created_at)
    select e.id, '5eed0000-0000-4000-8000-000000000001'::uuid, me, 'Około 40 minut z przystankami.', e.created_at + interval '360 minutes'
    from feed_event e where e.id = (select e.id from feed_event e join route r on r.id = e.route_id where r.user_id = '5eed0000-0000-4000-8000-000000000001'::uuid and r.name = 'Wieczorny spacer po Starówce');
    insert into feed_comment (event_id, user_id, reply_to_user_id, body, created_at)
    select e.id, '5eed0000-0000-4000-8000-000000000002'::uuid, null, 'Jak teraz wygląda asfalt przy zalewie?', e.created_at + interval '200 minutes'
    from feed_event e where e.id = (select e.id from feed_event e join route r on r.id = e.route_id where r.user_id = '5eed0000-0000-4000-8000-000000000001'::uuid and r.name = 'Bulwary i Zalew Zemborzycki');
    insert into feed_comment (event_id, user_id, reply_to_user_id, body, created_at)
    select e.id, '5eed0000-0000-4000-8000-000000000001'::uuid, '5eed0000-0000-4000-8000-000000000002'::uuid, 'W tym roku odnowili odcinek przy plaży.', e.created_at + interval '260 minutes'
    from feed_event e where e.id = (select e.id from feed_event e join route r on r.id = e.route_id where r.user_id = '5eed0000-0000-4000-8000-000000000001'::uuid and r.name = 'Bulwary i Zalew Zemborzycki');
    insert into feed_comment (event_id, user_id, reply_to_user_id, body, created_at)
    select e.id, '5eed0000-0000-4000-8000-000000000002'::uuid, null, 'Planuję w weekend, jaka nawierzchnia?', e.created_at + interval '120 minutes'
    from feed_event e where e.id = (select e.id from feed_event e join route r on r.id = e.route_id where r.user_id = '5eed0000-0000-4000-8000-000000000004'::uuid and r.name = 'Lublin - Kozłówka');
    insert into feed_comment (event_id, user_id, reply_to_user_id, body, created_at)
    select e.id, '5eed0000-0000-4000-8000-000000000004'::uuid, '5eed0000-0000-4000-8000-000000000002'::uuid, 'Głównie asfalt, końcówka szutrowa.', e.created_at + interval '180 minutes'
    from feed_event e where e.id = (select e.id from feed_event e join route r on r.id = e.route_id where r.user_id = '5eed0000-0000-4000-8000-000000000004'::uuid and r.name = 'Lublin - Kozłówka');
    insert into feed_comment (event_id, user_id, reply_to_user_id, body, created_at)
    select e.id, me, '5eed0000-0000-4000-8000-000000000002'::uuid, 'Dołączam do planów!', e.created_at + interval '400 minutes'
    from feed_event e where e.id = (select e.id from feed_event e join route r on r.id = e.route_id where r.user_id = '5eed0000-0000-4000-8000-000000000004'::uuid and r.name = 'Lublin - Kozłówka');
    insert into feed_comment (event_id, user_id, reply_to_user_id, body, created_at)
    select e.id, me, null, 'Świetne tempo!', e.created_at + interval '60 minutes'
    from feed_event e where e.id = (select e.id from feed_event e join activity a on a.id = e.activity_id where a.user_id = '5eed0000-0000-4000-8000-000000000003'::uuid and a.client_id = '5eed0008-0000-4000-8000-00000000c11d');
    insert into feed_comment (event_id, user_id, reply_to_user_id, body, created_at)
    select e.id, '5eed0000-0000-4000-8000-000000000003'::uuid, me, 'Dzięki, pogoda pomogła!', e.created_at + interval '100 minutes'
    from feed_event e where e.id = (select e.id from feed_event e join activity a on a.id = e.activity_id where a.user_id = '5eed0000-0000-4000-8000-000000000003'::uuid and a.client_id = '5eed0008-0000-4000-8000-00000000c11d');
    insert into feed_comment (event_id, user_id, reply_to_user_id, body, created_at)
    select e.id, '5eed0000-0000-4000-8000-000000000001'::uuid, null, 'Dobra średnia!', e.created_at + interval '45 minutes'
    from feed_event e where e.id = (select e.id from feed_event e join activity a on a.id = e.activity_id where a.user_id = '5eed0000-0000-4000-8000-000000000002'::uuid and a.client_id = '5eed0005-0000-4000-8000-00000000c11d');
    insert into feed_comment (event_id, user_id, reply_to_user_id, body, created_at)
    select e.id, '5eed0000-0000-4000-8000-000000000006'::uuid, null, 'Też ją dziś zaliczyłem!', e.created_at + interval '500 minutes'
    from feed_event e where e.id = (select e.id from feed_event e join poi_collection pc on pc.id = e.collection_id join point_of_interest p on p.id = pc.poi_id where pc.user_id = '5eed0000-0000-4000-8000-000000000001'::uuid and p.name = 'Brama Krakowska');
    insert into feed_comment (event_id, user_id, reply_to_user_id, body, created_at)
    select e.id, '5eed0000-0000-4000-8000-000000000007'::uuid, null, 'Polecam zajrzeć wiosną, wtedy najładniej.', e.created_at + interval '80 minutes'
    from feed_event e where e.id = (select e.id from feed_event e join poi_collection pc on pc.id = e.collection_id join point_of_interest p on p.id = pc.poi_id where pc.user_id = '5eed0000-0000-4000-8000-000000000003'::uuid and p.name = 'Ogród Botaniczny UMCS');

    raise notice 'Dodano % kont testowych. Konto główne: %', 8, me;
end $$;
