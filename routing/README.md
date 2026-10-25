# Routing (GraphHopper)

Silnik wyznaczania tras dla planowania trasy w aplikacji (tydzień 5). Działa lokalnie w Dockerze
na danych OpenStreetMap dla województwa lubelskiego.

## Pierwsze uruchomienie
Z głównego folderu repozytorium (Git Bash):

```bash
cd routing && ./download-data.sh && cd ..       # pobranie danych OSM (jednorazowo)
docker compose up -d --build graphhopper        # budowa obrazu i start
docker compose logs -f graphhopper              # poczekaj na "Server - Started" (pierwszy start: kilka minut, import danych)
```

## Sprawdzenie
```bash
curl "http://localhost:8989/info"
curl "http://localhost:8989/route?point=51.2465,22.5684&point=51.2480,22.5300&profile=bike&points_encoded=false&instructions=false"
curl "http://localhost:8989/route?point=51.2465,22.5684&point=51.2480,22.5300&profile=foot&points_encoded=false&instructions=false"
```
Odpowiedź zawiera `paths[0].distance` (metry), `paths[0].time` (milisekundy) i `paths[0].points` (GeoJSON, współrzędne `[długość, szerokość]`).
`/info` powinno listować profile `bike` i `foot`.

## Codzienna praca
- Start/stop: `docker compose up -d graphhopper` / `docker compose stop graphhopper`.
- Po zmianie `config.yml`: `docker compose down`, usuń folder `routing/data/graph-cache`, uruchom z `--build`.
- Dane i graf są w `routing/data/` (poza Gitem). Przy nowym regionie podmień plik `.pbf` i ustaw `OSM_PBF`.
- Port 8989 jest otwarty tylko dla localhost; aplikacja będzie łączyć się z nim przez backend.
