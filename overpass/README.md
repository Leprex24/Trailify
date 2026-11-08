# Overpass (warstwy mapy)

Własny serwer Overpass API z danymi OpenStreetMap dla województwa lubelskiego. Backend pobiera z niego ścieżki
rowerowe, szlaki piesze i ścieżki leśne (`GET /api/v1/map-layers/{type}`), więc aplikacja nie zależy od przeciążonych
publicznych serwerów Overpass. Publiczne serwery zostają w aplikacji tylko jako awaryjne.

## Pierwsze uruchomienie
Z głównego folderu repozytorium (Git Bash):

```bash
docker compose up -d overpass
docker compose logs -f overpass      # Ctrl+C kończy podgląd, kontener działa dalej
```

Kontener sam pobiera dane (ok. 150 MB) i buduje bazę. To trwa kilkanaście minut i jest jednorazowe (baza zostaje
w wolumenie `trailify-overpass-data`). Gdy po imporcie kontener się zatrzyma, uruchom go jeszcze raz tym samym poleceniem
(`docker compose up -d overpass`).

## Sprawdzenie
```bash
curl -s "http://localhost:8081/api/interpreter" --data-urlencode 'data=[out:json];way["highway"="cycleway"](51.24,22.55,51.25,22.57);out skel geom 5;'
```
Odpowiedź to JSON z polem `elements`. Pusta lista w centrum Lublina oznacza, że import jeszcze trwa albo się nie udał.

## Codzienna praca
- Start/stop: `docker compose up -d overpass` / `docker compose stop overpass`.
- Port 8081 jest otwarty tylko dla localhost; aplikacja łączy się z backendem, nigdy bezpośrednio z Overpass.
- Odświeżenie danych (nowsza wersja OSM): `docker compose stop overpass`, `docker compose rm -f overpass`, `docker volume rm trailify_trailify-overpass-data`,
  `docker compose up -d overpass` (import od nowa). Nazwę wolumenu pokaże `docker volume ls`.
