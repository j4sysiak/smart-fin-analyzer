Lab91
-----

Lab91--Streaming Data Export -- Eksport CSV bez obciążania RAM-u
================================================================

Cel labu
--------

Celem tego etapu było wdrożenie eksportu transakcji do CSV tak, aby:
- działał dla dużych wolumenów danych,
- nie mieszał danych pomiędzy użytkownikami,
- był poprawnie obsługiwany po stronie HTTP (nagłówki, nazwa pliku, UTF-8 BOM),
- dostarczał metryki techniczne do monitoringu.

Co zostało zaimplementowane
---------------------------

1) Endpoint eksportu CSV
------------------------

W `src/main/groovy/pl/edu/praktyki/web/TransactionController.groovy` dodano endpoint:

- `GET /api/transactions/export`

Logika endpointu:
- generuje nazwę pliku przez `ExportService.buildDownloadFilename()`,
- dopisuje UTF-8 BOM (`0xEF 0xBB 0xBF`),
- wywołuje `ExportService.exportToCsv(writer)`,
- ustawia nagłówki HTTP:
  - `Content-Type: text/csv; charset=UTF-8`
  - `Content-Disposition: attachment; filename="transactions_<user>_<yyyyMMdd_HHmmss>.csv"`
  - `Cache-Control: no-store, no-cache, must-revalidate, max-age=0`
  - `Pragma: no-cache`
  - `Expires: 0`
  - `Content-Length`

2) Serwis eksportu
------------------

W `src/main/groovy/pl/edu/praktyki/service/ExportService.groovy` zaimplementowano:

- `buildDownloadFilename()`
  - normalizacja nazwy użytkownika (`sanitizeFilenamePart`),
  - timestamp w formacie `yyyyMMdd_HHmmss`,
  - format końcowy: `transactions_<user>_<timestamp>.csv`.

- `exportToCsv(Writer writer)`
  - eksport tylko danych aktualnie zalogowanego użytkownika,
  - zapis nagłówka CSV: `ID,Data,Kwota_PLN,Kategoria,Opis`,
  - iteracja po rekordach i zapis linia-po-linii,
  - flush co 1000 rekordów,
  - finalny flush i log podsumowujący.

- `csvField(Object value)`
  - escapowanie pól CSV (przecinki, cudzysłowy, nowa linia),
  - podwajanie cudzysłowów zgodnie ze standardem CSV.

3) Strumieniowy odczyt z bazy
-----------------------------

W `src/main/groovy/pl/edu/praktyki/repository/TransactionRepository.groovy` dodano:

- `streamAllByOwnerUsername(String username)` z `@Query`

To pozwala czytać rekordy jako `Stream<TransactionEntity>` zamiast pobierać wszystko jednorazowo do listy.

4) Metryki i logowanie
----------------------

W `ExportService` dodano metryki Micrometer:
- timer: `export.csv.timer` (tagowany `username`),
- licznik wierszy: `export.csv.rows.total`,
- gauge bajtów: `export.csv.bytes.total`.

Dodatkowo dodano logi start/koniec eksportu i postęp co 1000 rekordów.

5) Izolacja danych
------------------

Eksport działa per użytkownik (`ownerUsername`) i zwraca wyłącznie jego rekordy.

Jak to przetestowaliśmy
-----------------------

W `src/test/groovy/pl/edu/praktyki/web/ExportSpec.groovy` pokryto:

1. Poprawny eksport jednego użytkownika:
- status HTTP 200,
- poprawne nagłówki,
- poprawna treść CSV,
- brak rekordów obcego użytkownika.

2. Smoke test dużego wolumenu (5000 rekordów):
- poprawny wynik i nagłówki,
- obecność UTF-8 BOM,
- dokładna liczba linii (`header + 5000`),
- brak rekordów obcych użytkowników.

3. Format nazwy pliku:
- `transactions_user_YYYYMMDD_HHMMSS.csv`.

Wnioski techniczne
------------------

Co działa bardzo dobrze:
- odczyt danych z DB jest strumieniowy,
- CSV jest poprawnie escapowany,
- eksport jest izolowany per użytkownik,
- endpoint jest gotowy pod frontend (nagłówki i nazwa pliku),
- mamy observability przez metryki i logi.

Uwaga architektoniczna:
- Aktualnie kontroler składa wynik w `ByteArrayOutputStream`, więc finalna odpowiedź jest buforowana w pamięci przed wysyłką.
- Odczyt z bazy jest strumieniowy, ale wysyłka HTTP jeszcze nie jest "czystym streamingiem" chunkowanym.
- To jest akceptowalne na tym etapie, a pełne streamowanie odpowiedzi można zrobić jako kolejny krok optymalizacyjny.

Szybkie uruchomienie testów
---------------------------

```powershell
.\gradlew.bat test --tests "pl.edu.praktyki.web.ExportSpec" -i
```

Podsumowanie labu
-----------------

Lab91 dostarczył kompletny mechanizm eksportu CSV:
- bezpieczny biznesowo (izolacja użytkowników),
- stabilny technicznie (testy i metryki),
- gotowy do dalszego rozwoju pod odporność i idempotentność w kolejnym etapie (Lab92).
