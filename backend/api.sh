#!/usr/bin/env bash
# Pomocnik do ręcznego testowania API (tylko lokalnie). Loguje się w Supabase i wysyła żądanie do backendu.
# Użycie (z głównego folderu repo):
#   bash backend/api.sh METODA /sciezka ['{"json":"body"}']
#   bash backend/api.sh GET '/routes?sort=newest'
# E-mail i hasło pyta za każdym razem albo bierze ze zmiennych TRAILIFY_EMAIL i TRAILIFY_PASSWORD.
set -euo pipefail
cd "$(dirname "$0")/.."

# wartość klucza z pliku .env / secrets.properties (bez znaków \r z Windows)
val() { grep "^$2=" "$1" | head -1 | cut -d= -f2- | tr -d '\r'; }

SUPABASE_URL=$(val .env SUPABASE_URL)
PUBLISHABLE_KEY=$(val android/secrets.properties SUPABASE_PUBLISHABLE_KEY)
EMAIL=${TRAILIFY_EMAIL:-}
PASSWORD=${TRAILIFY_PASSWORD:-}
if [ -z "$EMAIL" ]; then read -r -p "E-mail: " EMAIL; fi
if [ -z "$PASSWORD" ]; then read -r -s -p "Hasło: " PASSWORD; echo >&2; fi

# znaki specjalne w haśle nie mogą zepsuć JSON-a
ESCAPED=${PASSWORD//\\/\\\\}
ESCAPED=${ESCAPED//\"/\\\"}

LOGIN=$(curl -s -X POST "$SUPABASE_URL/auth/v1/token?grant_type=password" \
  -H "apikey: $PUBLISHABLE_KEY" -H "Content-Type: application/json" \
  -d "{\"email\":\"$EMAIL\",\"password\":\"$ESCAPED\"}")
TOKEN=$(printf '%s' "$LOGIN" | sed -n 's/.*"access_token":"\([^"]*\)".*/\1/p')
if [ -z "$TOKEN" ]; then
  echo "Logowanie nie powiodło się: ${LOGIN:0:300}" >&2
  exit 1
fi

ARGS=()
if [ $# -ge 3 ]; then ARGS=(-d "$3"); fi
curl -s -X "$1" "http://localhost:8080/api/v1$2" \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  "${ARGS[@]}" -w '\nHTTP %{http_code}\n'
