#!/usr/bin/env bash
# Pobiera dane OpenStreetMap dla województwa lubelskiego (ok. 100 MB) z Geofabrik.
# Uruchom z folderu routing/:  ./download-data.sh
# Inny region: zmień adres poniżej i zmienną OSM_PBF w docker-compose.yml.
set -euo pipefail
cd "$(dirname "$0")"
mkdir -p data
curl -L --fail --progress-bar -o data/lubelskie-latest.osm.pbf \
  https://download.geofabrik.de/europe/poland/lubelskie-latest.osm.pbf
echo "Gotowe: $(du -h data/lubelskie-latest.osm.pbf | cut -f1) w routing/data/"
