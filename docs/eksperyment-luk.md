# Eksperyment: dokładność odtwarzania luk w GPS

Narzędzie z etapu 4 rekonstrukcji luk. Z prawdziwych nagrań wycina sztuczne luki o znanym przebiegu, odtwarza je
kilkoma metodami (linia prosta, najkrótsza trasa po ścieżkach, ocena propozycji z profilem tempa i bez niego)
i mierzy, jak bardzo wynik różni się od trasy, którą użytkownik naprawdę przeszedł. Dobiera też wagi oceny
(`GapWeights`) na części tras i sprawdza je na pozostałych.

## 1. Dane

W Supabase (SQL Editor) uruchom zapytanie i pobierz wynik jako CSV (przycisk "Download CSV"). Zapytanie bierze tylko
punkty zmierzone przez GPS (bez odtworzonych) z Twoich aktywności, które mają co najmniej 5 minut nagrania:

```sql
select a.id as activity_id, a.type, extract(epoch from p.recorded_at) as t, p.latitude, p.longitude
from gps_point p
join activity a on a.id = p.activity_id
where not p.is_reconstructed
  and a.user_id = (select user_id from activity order by id desc limit 1)
  and a.duration_seconds >= 300
order by a.id, p.recorded_at;
```

Zapisz plik np. jako `C:\Studia\inzynierka\dane\punkty.csv` (katalog poza repozytorium, bo to dane o lokalizacji).

## 2. Uruchomienie

GraphHopper musi działać (`docker compose up -d graphhopper`). W Git Bash:

```bash
cd /c/Studia/inzynierka/Apka/trailify/android
export TRAILIFY_EXPERIMENT_CSV="C:/Studia/inzynierka/dane/punkty.csv"
./gradlew :core-domain:testDebugUnitTest --tests "*GapExperiment*" --rerun
```

Wyniki zapisują się obok pliku CSV: `experiment_results.md` (tabele do pracy), `experiment_windows.csv` (każda luka
osobno) i `routing_cache.tsv` (zapamiętane odpowiedzi GraphHoppera, żeby kolejne uruchomienia były szybkie).
Bez zmiennej `TRAILIFY_EXPERIMENT_CSV` test jest pomijany i nie przeszkadza w zwykłym `./gradlew test`.

Opcjonalne zmienne: `TRAILIFY_GRAPHHOPPER_URL` (domyślnie `http://localhost:8989`), `TRAILIFY_EXPERIMENT_OUT`
(katalog wyników).

## 3. Ograniczenia

- Kroki z krokomierza nie są w bazie (tabela `gps_point` ich nie ma), więc eksperyment ocenia tempo i kierunek,
  ale nie wpływ kroków.
- Wyniki zależą od liczby i różnorodności nagrań; przy kilku trasach jednej osoby dobór wag jest orientacyjny.
