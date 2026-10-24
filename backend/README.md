# Trailify: backend

Spring Boot 4 (Kotlin, Gradle), PostgreSQL + PostGIS (Supabase), walidacja tokenów JWT z Supabase Auth.
Dostęp do bazy: `JdbcClient` (czyste SQL), bez Hibernate Spatial na razie.

## Wymagania
- JDK 21
- IntelliJ IDEA (Community wystarczy) lub Android Studio do otwarcia projektu
- Plik `.env` w głównym folderze repo (wzór: `.env.example`)

## Konfiguracja `.env`
Wartości bez cudzysłowów i bez spacji wokół `=`:

```
SUPABASE_URL=https://xxxxxxxx.supabase.co
SUPABASE_SECRET_KEY=sb_secret_...
DB_URL=jdbc:postgresql://aws-...pooler.supabase.com:5432/postgres
DB_USER=postgres.xxxxxxxx
DB_PASSWORD=...
```

`SUPABASE_SECRET_KEY` jest potrzebny tylko do `DELETE /api/v1/me` (usuwa konto z Supabase Auth i pliki ze Storage).
Bez niego ten endpoint zwraca 503, reszta działa. Klucz zostaje tylko na backendzie.

`SUPABASE_JWKS_URL` jest opcjonalne (domyślnie `$SUPABASE_URL/auth/v1/.well-known/jwks.json`).

## Uruchomienie
Z folderu `backend/`:

```
./gradlew bootRun          # Windows: gradlew.bat bootRun
./gradlew test             # testy jednostkowe (nie wymagają bazy)
```

Przy pierwszym starcie Flyway wykonuje migracje z `src/main/resources/db/migration`.
W logu powinno być `Successfully applied 4 migrations` (przy kolejnych startach `Schema ... is up to date`).

### Sprawdzenie
1. Stan aplikacji (bez tokena): `GET http://localhost:8080/actuator/health` → `{"status":"UP"}`
2. Bez tokena endpoint chroniony zwraca 401:
   `GET http://localhost:8080/api/v1/me` → `{"code":"UNAUTHORIZED",...}`
3. Z tokenem:
   - W Supabase: **Authentication → Users → Add user → Create new user** (e-mail, hasło, zaznacz *Auto Confirm User*).
   - Pobierz token (Postman lub curl):
     `POST https://<ID>.supabase.co/auth/v1/token?grant_type=password`
     nagłówki: `apikey: <publishable key>`, `Content-Type: application/json`
     body: `{"email":"...","password":"..."}` → pole `access_token`
   - `GET http://localhost:8080/api/v1/me` z nagłówkiem `Authorization: Bearer <access_token>`
     → profil z bazy: `{"id":"...","email":"...","displayName":"...","role":"USER","createdAt":"...","stats":{...}}`
4. Rola i blokada konta (zmiana w SQL Editor w Supabase, działa od następnego żądania):
   - administrator: `update profile set role = 'ADMIN' where email = 'twoj@email';`
     (wtedy `/api/v1/admin/...` przestaje zwracać 403)
   - blokada: `update profile set status = 'BLOCKED' where email = '...';`
     (każde żądanie z tokenem tego konta zwraca 403 `ACCOUNT_BLOCKED`; cofnięcie: `status = 'ACTIVE'`)

Token ważny jest około godziny, potem pobierz nowy.

## Lokalna baza zamiast Supabase (opcjonalnie)
```
docker compose up -d db                      # z głównego folderu repo
SPRING_PROFILES_ACTIVE=local ./gradlew bootRun
```
Lokalna baza ma uproszczony schemat `auth` (`docker/local-init`), więc użytkowników z Supabase trzeba
w niej dopisać ręcznie (ten sam UUID co `userId` z `whoami`), żeby endpointy używające bazy znały profil.

## Zasady
- **Migracje pisze tylko autor rdzenia.** Potrzebujesz zmiany w bazie? Napisz, a nie dodawaj pliku `V5__...`,
  bo dwa pliki o tym samym numerze zablokują start aplikacji.
- Nigdy nie zmieniaj istniejących migracji `V1`–`V4` po ich uruchomieniu (Flyway zapamiętuje sumę kontrolną). Zmiana = nowa migracja.
- Każda nowa tabela ma mieć `enable row level security` (patrz V2/V3).
- Rola (`USER`/`ADMIN`) i status konta (`ACTIVE`/`BLOCKED`) pochodzą z tabeli `profile`, nie z tokena.
  Profil jest wczytywany z bazy przy każdym żądaniu (patrz `ProfileJwtAuthenticationConverter`).

## Typowe problemy
| Objaw | Przyczyna |
|---|---|
| `Could not resolve placeholder 'DB_URL'` | brak `.env` w głównym folderze repo albo uruchomienie z innego katalogu niż `backend/` |
| Timeout / `Connection refused` do bazy | użyto adresu *Direct connection* (IPv6). Skopiuj *Session pooler* z przycisku Connect |
| `password authentication failed` | zły `DB_USER` (musi być `postgres.<ID projektu>`) lub hasło |
| 401 mimo poprawnego tokena | zły `SUPABASE_URL` (issuer nie pasuje) albo token wygasł |
| `Unsupported Database: PostgreSQL` | brak zależności `org.flywaydb:flyway-database-postgresql` w `build.gradle.kts` |
