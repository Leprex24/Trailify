"""Generuje backend/seed/seed.sql (dane testowe: trasy i ciekawe punkty). Użycie: python generate_seed.py seed.sql"""
import math
import sys

sys.stdout.reconfigure(encoding="utf-8")


def hav(a, b):
    r = 6371000.0
    la1, lo1, la2, lo2 = map(math.radians, (a[0], a[1], b[0], b[1]))
    d = math.sin((la2 - la1) / 2) ** 2 + math.cos(la1) * math.cos(la2) * math.sin((lo2 - lo1) / 2) ** 2
    return 2 * r * math.asin(math.sqrt(d))


# (nazwa, opis, typ, turystyczna, widokowa, [(lat, lng), ...], liczba zdjęć)
ROUTES = [
    ("Pętla wokół Zalewu Zemborzyckiego", "Asfaltowa ścieżka rowerowa dookoła zalewu. Płasko, dużo widoków na wodę, po drodze plaże i przystań.",
     "CYCLING", True, True,
     [(51.2190, 22.6120), (51.2150, 22.6200), (51.2085, 22.6290), (51.2010, 22.6290), (51.1960, 22.6200),
      (51.1950, 22.6080), (51.1990, 22.5980), (51.2060, 22.5960), (51.2130, 22.6010), (51.2190, 22.6120)], 3),
    ("Stare Miasto i Zamek Lubelski", "Krótki spacer od Placu Litewskiego przez Bramę Krakowską i Rynek do Zamku.",
     "WALKING", True, False,
     [(51.2484, 22.5617), (51.2482, 22.5635), (51.2481, 22.5650), (51.2477, 22.5675), (51.2479, 22.5688),
      (51.2486, 22.5697), (51.2493, 22.5705), (51.2502, 22.5722), (51.2505, 22.5735)], 2),
    ("Bulwary nad Bystrzycą", "Bieg wzdłuż rzeki od Dąbrowy do Podzamcza. Równa nawierzchnia, dobra na tempo.",
     "RUNNING", False, True,
     [(51.2397, 22.5395), (51.2410, 22.5480), (51.2425, 22.5560), (51.2440, 22.5640), (51.2455, 22.5720),
      (51.2468, 22.5800), (51.2470, 22.5830)], 1),
    ("Górki Czechowskie", "Pagórkowata pętla po terenie dawnego poligonu. Ścieżki ziemne, kilka mocnych podbiegów.",
     "RUNNING", False, True,
     [(51.2690, 22.5470), (51.2705, 22.5440), (51.2720, 22.5420), (51.2750, 22.5380), (51.2770, 22.5430),
      (51.2760, 22.5480), (51.2745, 22.5500), (51.2710, 22.5520), (51.2690, 22.5470)], 2),
    ("Lublin - Nałęczów", "Dłuższa wycieczka rowerowa do uzdrowiska. Większość trasy po drogach o małym ruchu.",
     "CYCLING", True, False,
     [(51.2470, 22.5250), (51.2550, 22.4900), (51.2630, 22.4500), (51.2700, 22.4000), (51.2780, 22.3500),
      (51.2830, 22.2900), (51.2860, 22.2160)], 1),
    ("Ogród Saski i Aleje Racławickie", "Spokojny spacer przez najstarszy park w mieście.",
     "WALKING", False, True,
     [(51.2492, 22.5585), (51.2505, 22.5570), (51.2518, 22.5560), (51.2530, 22.5545), (51.2525, 22.5525),
      (51.2512, 22.5535), (51.2500, 22.5555), (51.2492, 22.5585)], 1),
    ("Do Ogrodu Botanicznego UMCS", "Spacer z kampusu UMCS do ogrodu botanicznego wzdłuż Sławinkowskiej.",
     "WALKING", True, True,
     [(51.2465, 22.5430), (51.2480, 22.5380), (51.2500, 22.5320), (51.2530, 22.5260), (51.2560, 22.5200),
      (51.2590, 22.5160), (51.2615, 22.5145)], 1),
    ("Rowerem do Skansenu", "Trasa z centrum do Muzeum Wsi Lubelskiej, częściowo ścieżką rowerową.",
     "CYCLING", True, False,
     [(51.2480, 22.5600), (51.2500, 22.5500), (51.2520, 22.5400), (51.2570, 22.5200), (51.2600, 22.5080),
      (51.2620, 22.5000), (51.2640, 22.4930)], 0),
    ("Rezerwat Stary Gaj", "Leśna pętla biegowa w rezerwacie. Cień nawet w upał, miękka nawierzchnia.",
     "RUNNING", False, True,
     [(51.2090, 22.5180), (51.2060, 22.5120), (51.2030, 22.5150), (51.2010, 22.5220), (51.2040, 22.5280),
      (51.2075, 22.5260), (51.2090, 22.5180)], 0),
    ("Kazimierz Dolny - wąwozy lessowe", "Spacer po Rynku i wąwozach. Strome podejścia, piękne widoki na Wisłę.",
     "WALKING", True, True,
     [(51.3210, 21.9480), (51.3220, 21.9505), (51.3230, 21.9530), (51.3260, 21.9560), (51.3270, 21.9500),
      (51.3255, 21.9465), (51.3245, 21.9440), (51.3210, 21.9480)], 2),
]

POIS = [
    ("Brama Krakowska", "Gotycka brama miejska z XIV wieku, symbol Lublina.", 51.2477, 22.5675, "APPROVED"),
    ("Zamek Lubelski", "Zamek z kaplicą Trójcy Świętej z bizantyjsko-ruskimi freskami.", 51.2502, 22.5722, "APPROVED"),
    ("Wieża Trynitarska", "Punkt widokowy na Stare Miasto, ponad 40 m wysokości.", 51.2470, 22.5665, "APPROVED"),
    ("Brama Grodzka", "Brama łącząca Stare Miasto z zamkiem, dawniej granica dzielnicy żydowskiej.", 51.2493, 22.5705, "APPROVED"),
    ("Lubelska Trasa Podziemna", "Trasa przez połączone piwnice kamienic pod Rynkiem.", 51.2478, 22.5686, "APPROVED"),
    ("Plac Litewski", "Główny plac miasta z fontanną multimedialną.", 51.2484, 22.5617, "APPROVED"),
    ("Ogród Saski", "Najstarszy park publiczny w Lublinie, otwarty w 1837 roku.", 51.2525, 22.5555, "APPROVED"),
    ("Centrum Spotkania Kultur", "Nowoczesne centrum kultury z ogrodem na dachu.", 51.2470, 22.5560, "APPROVED"),
    ("Ogród Botaniczny UMCS", "Ogród z kolekcją ponad 6 tysięcy gatunków roślin.", 51.2615, 22.5145, "APPROVED"),
    ("Muzeum Wsi Lubelskiej", "Skansen z dawną zabudową wsi i miasteczka.", 51.2640, 22.4930, "APPROVED"),
    ("Plaża nad Zalewem Zemborzyckim", "Kąpielisko i przystań przy ścieżce rowerowej.", 51.2160, 22.6175, "APPROVED"),
    ("Rezerwat Stary Gaj", "Rezerwat leśny z grądem i buczyną.", 51.2050, 22.5180, "APPROVED"),
    ("Górki Czechowskie - punkt widokowy", "Widok na panoramę północnej części miasta.", 51.2745, 22.5420, "APPROVED"),
    ("Mural na Zielonej", "Duży mural na ścianie kamienicy (zgłoszenie do sprawdzenia).", 51.2468, 22.5598, "PENDING"),
    ("Punkt widokowy nad Bystrzycą", "Kładka z widokiem na rzekę (zgłoszenie do sprawdzenia).", 51.2440, 22.5640, "PENDING"),
]

SPEED = {"CYCLING": 18 / 3.6, "RUNNING": 10 / 3.6, "WALKING": 4.5 / 3.6}


def q(s):
    return "'" + s.replace("'", "''") + "'"


out = []
w = out.append
w("-- =====================================================================")
w("-- Trailify: dane testowe (tydzień 8) - trasy i ciekawe punkty w Lublinie i okolicy.")
w("-- Plik wygenerowany; współrzędne są przybliżone (do testów i prezentacji).")
w("--")
w("-- Uruchomienie: Supabase -> SQL Editor -> wklej całość -> Run (albo psql na bazie z migracjami V1-V4).")
w("-- Skrypt można uruchamiać wielokrotnie: najpierw usuwa dane z poprzedniego uruchomienia (po nazwach).")
w("--")
w("-- Właściciel tras: pierwszy administrator, a gdy go nie ma - najstarsze konto.")
w("-- Musi istnieć co najmniej jedno konto (zarejestruj się w aplikacji przed uruchomieniem).")
w("-- Punkty POI nie mają zgłaszającego (jak dodane przez administratora).")
w("-- =====================================================================")
w("")
w("-- funkcje PostGIS leżą w schemacie extensions (Supabase)")
w("set search_path to public, extensions;")
w("")
w("do $$")
w("declare")
w("    owner uuid;")
w("    new_id bigint;")
w("begin")
w("    select id into owner from profile order by (role = 'ADMIN') desc, created_at limit 1;")
w("    if owner is null then")
w("        raise exception 'Brak kont w tabeli profile: najpierw zarejestruj się w aplikacji';")
w("    end if;")
w("")
w("    -- poprzednie uruchomienie (zdjęcia, recenzje i kolekcje znikają kaskadowo)")
w("    delete from route where user_id = owner and name in (" + ", ".join(q(r[0]) for r in ROUTES) + ");")
w("    delete from point_of_interest where submitted_by is null and name in ("
  + ", ".join(q(p[0]) for p in POIS) + ");")
w("    delete from photo where storage_key like 'seed/%';")
w("")
summary = []
for i, (name, desc, typ, tourist, scenic, pts, photos) in enumerate(ROUTES, 1):
    wkt = "LINESTRING(" + ", ".join(f"{lng} {lat}" for lat, lng in pts) + ")"
    length = sum(hav(pts[k], pts[k + 1]) for k in range(len(pts) - 1))
    duration = int(length / SPEED[typ])
    summary.append((name, typ, length, duration))
    w(f"    -- {i}. {name}: ok. {length / 1000:.1f} km")
    w("    insert into route (user_id, name, description, activity_type, is_tourist, is_scenic, is_public,")
    w("                       total_distance, duration_seconds, geometry, created_at)")
    w(f"    select owner, {q(name)}, {q(desc)}, '{typ}', {str(tourist).lower()}, {str(scenic).lower()}, true,")
    w(f"           ST_Length(g::geography), {duration}, g, now() - interval '{len(ROUTES) - i} days'")
    w(f"    from ST_SetSRID(ST_GeomFromText('{wkt}'), 4326) as g")
    w("    returning id into new_id;")
    for k in range(photos):
        key = f"seed/route-{i}-{k + 1}.jpg"
        url = f"https://picsum.photos/seed/trailify-route-{i}-{k + 1}/800/600"
        w(f"    insert into photo (route_id, url, storage_key) values (new_id, {q(url)}, {q(key)});")
    w("")
w("    -- ciekawe punkty")
for name, desc, lat, lng, status in POIS:
    w("    insert into point_of_interest (submitted_by, name, description, latitude, longitude, status)")
    w(f"    values (null, {q(name)}, {q(desc)}, {lat}, {lng}, '{status}');")
w("")
w(f"    raise notice 'Dodano {len(ROUTES)} tras i {len(POIS)} punktów (właściciel tras: %)', owner;")
w("end $$;")

open(sys.argv[1], "w", encoding="utf-8", newline="\n").write("\n".join(out) + "\n")
for name, typ, length, duration in summary:
    print(f"{typ:8} {length / 1000:6.1f} km {duration // 60:4d} min  {name}")
