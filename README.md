# Trailify

System do planowania i współdzielenia tras wycieczkowych z klientem mobilnym (Android).
Praca dyplomowa inżynierska, Informatyka, specjalność Inżynieria oprogramowania, Lublin 2026.

**Autorzy:** Kacper Sawicki, Mikołaj Lis
**Promotor:** dr inż. Piotr Kopniak

## Opis
Aplikacja pozwala planować trasy rowerowe, biegowe i spacerowe na mapie, rejestrować
aktywności z użyciem GPS i krokomierza oraz udostępniać trasy innym użytkownikom.
Trasy można oceniać i wyszukiwać, a w terenie zbierać ciekawe punkty (POI).
Dodatkowo aplikacja korzysta z AI do rekonstrukcji trasy przy utracie sygnału GPS
oraz do planowania tras na podstawie preferencji użytkownika.

## Technologie
| Obszar | Narzędzie |
|---|---|
| Klient mobilny | Kotlin, Jetpack Compose, Android Studio |
| Mapy | MapLibre SDK, OpenStreetMap |
| Routing | GraphHopper (self-hosted) |
| Lokalizacja | FusedLocationProviderClient, Foreground Service |
| Backend | Spring Boot (Kotlin) |
| Baza danych | PostgreSQL + PostGIS |
| Auth i pliki | Supabase (Auth, Storage) |
| AI | Claude API (wywołania tylko z backendu) |

## Struktura repozytorium
- `android/`: aplikacja mobilna
- `backend/`: serwer Spring Boot
- `docs/`: dokumentacja, specyfikacja API (`openapi.yaml`)

## Wymagania
- Android Studio (aktualna stabilna wersja), JDK 17+
- Urządzenie lub emulator z Androidem 8.0 (API 26) lub nowszym
- TODO: Docker (baza PostGIS i GraphHopper lokalnie)

## Uruchomienie
### Aplikacja Android
1. Otwórz folder `android/` w Android Studio.
2. Utwórz plik `android/secrets.properties` (patrz sekcja Konfiguracja).
3. Uruchom konfigurację `app` na emulatorze lub urządzeniu.

### Backend
TODO

## Konfiguracja
Klucze i hasła nie trafiają do repozytorium. Wartości do plików `secrets.properties`
i `.env` dostajesz od autora projektu. TODO: opisać zmienne backendu (`.env`).

### `android/secrets.properties`
```properties
SUPABASE_URL=https://<projekt>.supabase.co
SUPABASE_PUBLISHABLE_KEY=sb_publishable_...
GOOGLE_WEB_CLIENT_ID=<Web Client ID z Google Cloud>.apps.googleusercontent.com
# opcjonalnie, domyślnie http://localhost:8080/api/v1
API_BASE_URL=http://localhost:8080/api/v1
```
- Bez `SUPABASE_URL` lub `SUPABASE_PUBLISHABLE_KEY` logowanie działa na atrapie.
- Bez `GOOGLE_WEB_CLIENT_ID` przycisk „Zaloguj przez Google” jest ukryty.
  To ma być **Web** Client ID (nie Android). Klient Android w Google Cloud ma
  package name `com.trailify.app` i SHA-1 z `gradlew signingReport` (każdy z nas dodaje swój).
- Profil (`/me`) aplikacja pobiera z backendu pod `API_BASE_URL` (tylko gdy są klucze Supabase).
  Backend na komputerze: uruchom `./gradlew bootRun` w `backend/`, a przy telefonie podpiętym
  kablem (lub emulatorze) wykonaj raz po podpięciu: `adb reverse tcp:8080 tcp:8080`.
  Bez działającego backendu ekrany Profil/Konto pokażą błąd połączenia.
- Po zmianie pliku: **File → Sync Project with Gradle Files**.
- Klucza `SUPABASE_SECRET_KEY` (secret / service_role) nigdy nie wpisujemy do aplikacji.

## Organizacja pracy
- `main` jest chroniony, zmiany wchodzą przez Pull Request z przeglądem drugiej osoby.
- Branche: `feature/<moduł>-<opis>`, np. `feature/auth-login-screen`.
- Commity po angielsku, w trybie rozkazującym, np. `Add login screen`.

## Status
Projekt w trakcie realizacji (start prac: 20.10.2026).