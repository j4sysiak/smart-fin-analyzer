# Smart-Fin-Analyzer — Uruchamianie aplikacji i testów

Aplikacja: `src\main\groovy\pl\edu\praktyki\SmartFinDbApp.groovy`

```
┌────────────────────────────────────────────────────────────────────────────┐
│  CAŁY PROJEKT UŻYWA POSTGRESQL (brak H2!)                                 │
│                                                                            │
│  Produkcja — TRYB DEV (zalecany lokalnie):                                │
│             docker compose up -d          ← tylko baza (smartfin-postgres) │
│             .\gradlew.bat runSmartFinDb  ← aplikacja przez Gradle/JVM    │
│             baza: smartfin_db, port 5432, finuser/finpass                 │
│             schemat: Flyway (ddl-auto=none)                               │
│                                                                            │
│  Produkcja — TRYB DOCKER (pełny kontener):                                │
│             .\gradlew.bat jibDockerBuild ← zbuduj obraz raz              │
│             docker compose up -d          ← baza + aplikacja w Dockerze   │
│                                                                            │
│  Testy      (.\gradlew.bat test):      PostgreSQL via Docker CLI          │
│             ├─ tryb 'tc'       (domyślny) — auto-kontener, port 15432     │
│             └─ tryb 'local-pg' (inspekcja) — ręczny kontener, port 5432   │
└────────────────────────────────────────────────────────────────────────────┘
```

## TL;DR — 4 najważniejsze komendy

```powershell
# tc — pełny, powtarzalny run
.\gradlew.bat "-Dspring.profiles.active=tc" "-Denable.flyway=true" clean test --no-daemon

# tc — pojedynczy test
.\gradlew.bat "-Dspring.profiles.active=tc" "-Denable.flyway=true" test --tests "pl.edu.praktyki.repository.CategorySpec" --no-daemon

# local-pg — start bazy + cleanup
docker compose up -d db
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\clean-db.ps1 -Mode local-pg -Force

# local-pg — pełny run
.\gradlew.bat "-Dlocal.pg=true" "-Denable.flyway=true" clean test --no-daemon
```

---

## 1. Produkcja — uruchomienie aplikacji z PostgreSQL

### Wymaganie: Docker Desktop musi być uruchomiony!

---

## TRYB DEV — zalecany do lokalnego developmentu

W tym trybie baza działa w Dockerze, a aplikacja uruchamiana jest przez Gradle (JVM na hoście).
Zalety: szybki restart, hot-reload, pełna widoczność logów, debugger działa od razu.

### Krok 1 — Uruchom **tylko bazę** (`docker compose`, bez serwisu `app`)

```powershell
docker compose stop
docker compose down -v
Get-Process java,javaw,gradle -ErrorAction SilentlyContinue | Select-Object Id,ProcessName,StartTime | Format-Table -AutoSize | Out-String

   Id ProcessName StartTime
   -- ----------- ---------
   25352 java        9.05.2026 21:24:25
   28332 java        10.05.2026 10:45:09

Stop-Process -Id 25352 -Force
Stop-Process -Id 28332 -Force

cd C:\dev\smart-fin-analyzer
docker compose up -d db
```

> `up -d db` — startuje tylko serwis `db` (kontener `smart-fin-analyzer` a w nim kontener bazy: smartfin-postgres), pomijając serwis `app`.
>   smart-fin-analyzer to nazwa projektu (z `docker-compose.yml`), a `db` to nazwa serwisu w tym pliku.
>   smartfin-postgres to nazwa kontenera, która jest zdefiniowana w `docker-compose.yml` → `services.db.container_name`.


To startuje kontener `smartfin-postgres` z:
- bazą `smartfin_db` (produkcja) i `smartfin_test` (testy local-pg, z `init-db.sql`)
- portem `5432`, użytkownikiem `finuser`, hasłem `finpass`
- wolumenem `postgres_data` (dane przetrwają restart kontenera)

Sprawdzenie, że kontener działa:
```powershell
docker ps | Select-String "postgres"
docker ps --filter name=smartfin-postgres
docker exec smartfin-postgres pg_isready -U finuser
```

### Krok 2 — Uruchom aplikację przez Gradle

**Sposób 1: Gradle task `runSmartFinDb` (zalecany)**

```powershell
.\gradlew.bat runSmartFinDb -PappArgs="-u Jacek"    # start aplikacji bez importu CSV

.\gradlew.bat runSmartFinDb -PappArgs="-u Jacek -f transactions_upload.csv"     # start aplikacji + import CSV

# jeśli chcesz tylko więcej logów Flyway przy starcie aplikacji:
.\gradlew.bat runSmartFinDb "-Dlogging.level.org.flywaydb=DEBUG" -PappArgs="-u Jacek -f transactions_upload.csv"
```

Gdy zobaczysz `80% EXECUTING [39s]` — aplikacja działa:
```text
<==========---> 80% EXECUTING [39s]
> :runSmartFinDb
```

**Sposób 2: bootRun**

```powershell
.\gradlew.bat bootRun
```

> **Uwaga:** Jeśli pojawi się błąd o zablokowanym pliku — sprawdź procesy Javy:
> ```powershell
> Get-Process java,javaw,gradle -ErrorAction SilentlyContinue | Select-Object Id,ProcessName,StartTime | Format-Table -AutoSize | Out-String
> Stop-Process -Id <PID> -Force
> ```

---

## TRYB DOCKER — pełne środowisko produkcyjne w kontenerach

W tym trybie zarówno baza jak i aplikacja działają w Dockerze (izolacja jak na produkcji).

### Krok 1 — Zbuduj obraz aplikacji (jednorazowo lub po każdej zmianie kodu)

```powershell
# Opcja A: Jib (szybszy, nie wymaga Dockerfile)
.\gradlew.bat jibDockerBuild

# Opcja B: `docker compose` z Dockerfile (automatyczna pełna budowa)
docker compose build app
```

### Krok 2 — Uruchom całe środowisko (baza + aplikacja)

```powershell
docker compose up -d
# lub przy zmianie kodu — przebuduj i uruchom:
docker compose up --build -d
```

Docker automatycznie:
1. Czeka aż PostgreSQL będzie gotowy (healthcheck)
2. Startuje aplikację — Flyway wykona migracje V1→V15

Sprawdzenie logów aplikacji:
```powershell
docker logs -f smartfin-app
```

---

### Parametry połączenia produkcyjnego (application.properties)

| Parametr       | Wartość                                                    |
|----------------|------------------------------------------------------------|
| Host:Port      | `localhost:5432`                                           |
| Baza           | `smartfin_db`                                              |
| User           | `finuser`                                                  |
| Password       | `finpass`                                                  |
| ddl-auto       | `none` ← **Flyway zarządza schematem** (nie Hibernate)     |
| Flyway         | `enabled=true`, `baseline-on-migrate=false`                |

### Zatrzymanie

```powershell
# Zatrzymaj aplikację (Ctrl+C lub:)
Get-Process java,javaw,gradle -ErrorAction SilentlyContinue | Select-Object Id,ProcessName,StartTime | Format-Table -AutoSize | Out-String
Stop-Process -Id <PID> -Force

# Zatrzymaj bazę (dane zostaną w wolumenie postgres_data):
docker compose stop

# LUB usuń kontener i dane:
docker compose down -v
```




---


## 2. Testy integracyjne — szybki start (`tc` i `local-pg`)

Aktualne źródło prawdy dla logiki testów to `src/test/groovy/pl/edu/praktyki/BaseIntegrationSpec.groovy`.
Ta klasa:
- domyślnie uruchamia profil `tc`,
- po `-Dlocal.pg=true` przełącza testy na profil `local-pg`,
- przy `-Denable.flyway=true` oddaje tworzenie schematu migracjom Flyway,
- czyści stan testów bezpieczniej niż wcześniej (osobne połączenie + `autocommit=true` dla TRUNCATE).

### `tc` — domyślny profil do powtarzalnych runów

W tym trybie `BaseIntegrationSpec` sam stawia testowy kontener PostgreSQL `smartfin-test-pg` na porcie `15432`.
Nie trzeba uruchamiać `docker compose`.

Pełny run:
```powershell
.\gradlew.bat "-Dspring.profiles.active=tc" "-Denable.flyway=true" clean test --no-daemon
```

Pojedynczy test:
```powershell
.\gradlew.bat "-Dspring.profiles.active=tc" "-Denable.flyway=true" test --tests "pl.edu.praktyki.repository.CategorySpec" --no-daemon
```

Opcjonalny ręczny cleanup (zwykle niepotrzebny):
```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\clean-db.ps1 -Mode tc -Force
```

### `local-pg` — profil do debugowania i inspekcji danych

W tym trybie testy łączą się z lokalnym PostgreSQL na `localhost:5432` / baza `smartfin_test`.
Przed pełnym runem zalecany jest jawny cleanup, żeby Flyway wystartował od czystego stanu.

1. Uruchom tylko bazę:
```powershell
docker compose up -d db
```

2. Wyczyść bazę testową:
```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\clean-db.ps1 -Mode local-pg -Force
```

3. Uruchom pełny zestaw testów:
```powershell
.\gradlew.bat "-Dlocal.pg=true" "-Denable.flyway=true" clean test --no-daemon
```

4. Jeśli chcesz zachować dane po teście do DBeavera / `psql`:
```powershell
.\gradlew.bat "-Dlocal.pg=true" "-Denable.flyway=true" "-Dlocal.pg.keepdata=true" test --tests "pl.edu.praktyki.repository.CategorySpec" --no-daemon
```

Szybki podgląd danych po runie:
```powershell
docker exec -it smartfin-postgres psql -U finuser -d smartfin_test
```

### Krótkie uwagi praktyczne

- W PowerShell cytuj każde `-D...`, np. `"-Dlocal.pg=true"`, bo inaczej Gradle może potraktować to jak nazwę taska.
- `local-pg` służy głównie do debugowania; do powtarzalnych pełnych runów wygodniejszy jest `tc`.
- Szczegóły konfiguracji są w `src/test/resources/application-local-pg.properties`, `src/test/resources/application-tc.properties` i `scripts/clean-db.ps1`.

### Transactional tests / rollback (w skrócie)

- `@Transactional` + `@Rollback` są dobre dla szybkich testów repozytoriów i prostych slice testów.
- Dla testów z async eventami / projectorami lepiej zostać przy pełnym commicie i izolacji opartej o czyszczenie stanu w `BaseIntegrationSpec`.

---





## 3. Dodanie przykładowych transakcji (wymaga działającej aplikacji)

```powershell
cd C:\dev\smart-fin-analyzer
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\add-sample-transaction.ps1
```

Sprawdzenie danych bezpośrednio w bazie:
```powershell
docker exec -it smartfin-postgres psql -U finuser -d smartfin_db -c "SELECT * FROM transactions LIMIT 10;"
```





---

## 4. Swagger UI (z autoryzacją JWT)

Wymaga działającej aplikacji na porcie 8080.

👉 http://localhost:8080/swagger-ui/index.html

### Jak się autoryzować w Swaggerze (3 kliknięcia):

**Krok 1 — Pobierz token:**
- Rozwiń sekcję **auth-controller** → `GET /api/auth/token`
- Kliknij **Try it out** → **Execute`
- Skopiuj wartość `token` z odpowiedzi (sam ciąg `eyJ...`, bez cudzysłowów)

**WAŻNE:** Jeśli chcesz wykonywać operacje administracyjne (np. upload pliku CSV) w Swaggerze
musisz użyć tokena wydanego dla użytkownika, który ma rolę `ROLE_ADMIN` w tabeli `users`.
Domyślnie możesz szybko uzyskać taki token przez:

```powershell
$token = (Invoke-RestMethod -Uri "http://localhost:8080/api/auth/token?user=admin" -Method Get).token
$token
```

Jeżeli chcesz, aby w Twojej bazie istniał trwały użytkownik admin, użyj endpointu rejestracji:

```powershell
Invoke-RestMethod -Uri "http://localhost:8080/api/auth/register" -Method Post -Body (@{username='admin'; password='secret'; role='ROLE_ADMIN'} | ConvertTo-Json) -ContentType 'application/json'
```
Po rejestracji sprawdź wpis w tabeli `users` (psql / DBeaver) — konto powinno mieć `role = ROLE_ADMIN`.

**Krok 2 — Autoryzuj się:**
- Kliknij przycisk **🔒 Authorize** (prawy górny róg strony)
- W polu **Value** wklej skopiowany token (BEZ prefiksu `Bearer` — Swagger doda go sam)
- Kliknij **Authorize** → **Close**

**Krok 3 — Korzystaj z API:**
- Teraz wszystkie requesty z Swagger UI będą zawierać nagłówek `Authorization: Bearer <token>`
- Możesz swobodnie testować `GET /api/transactions`, `POST /api/transactions` itp.

> **Uwaga:** Token jest ważny 1 godzinę. Po wygaśnięciu powtórz kroki 1–2.






---

## 5. Postman

Instrukcja: `C:\dev\smart-fin-analyzer\scripts\Readme--Odpalanie-RESTów-z-Postmana.md`

Kolekcja online: [Postman Web](https://web.postman.co/workspace/My-Workspace~f2aa92ac-63c8-420c-80c0-23d0d71ea517/collection/5972111-1831650e-83dc-4776-a5f8-4780294b7091?action=share&source=copy-link&creator=5972111)


### (Upload transactions from file) Wysyłanie plików (multipart/form-data) — ważne

Uwaga: jeśli używasz Postman Web (przeglądarka), to NIE MA on domyślnie dostępu do plików lokalnych — dlatego w przykładach możesz widzieć `@/path/to/file` zamiast rzeczywistej ścieżki. Masz trzy opcje:

- Zainstalować Postman Desktop (najprościej) — wtedy pole `form-data` → `file` działa bez dodatkowych czynności.
- Pozostać w Postman Web i zainstalować **Postman Agent** (mały program), który pozwala Webowi wysyłać lokalne pliki.
- Używać CLI (curl / PowerShell) — niezawodne i szybkie.

Przykłady (PowerShell i curl) — działają na Windows i są przydatne w CI / skryptach:

PowerShell (Invoke-RestMethod):
```powershell
$token = "eyJ...TWÓJ_TOKEN..."
$file = Get-Item "C:\dev\smart-fin-analyzer\transactions_upload.csv"
Invoke-RestMethod -Uri "http://localhost:8080/api/transactions/upload?user=Jacek" \
  -Method Post -Headers @{ Authorization = "Bearer $token" } -Form @{ file = $file }
```

curl.exe (PowerShell) — użyj `curl.exe`, nie aliasu PowerShell `curl`:
```powershell
curl.exe -v -X POST "http://localhost:8080/api/transactions/upload?user=Jacek" \
  -H "Authorization: Bearer eyJ...TWÓJ_TOKEN..." \
  -F "file=@C:/dev/smart-fin-analyzer/transactions_upload.csv;type=text/csv"
```

### Quick recipe — pobierz token admin i wyślij plik CSV (PowerShell + curl)

Poniżej minimalne, kopiowalne komendy, które użyłem do szybkiego testu (Windows PowerShell):

- Pobierz token JWT dla użytkownika `admin` (token będzie zawierał rolę `ROLE_ADMIN`):

```powershell
$token = (Invoke-RestMethod -Uri "http://localhost:8080/api/auth/token?user=admin" -Method Get).token
$token
```

- (opcjonalnie) Szybkie podejrzenie payloadu tokena (sprawdź, czy jest `ROLE_ADMIN`):

```powershell
$payload = $token.Split('.')[1]
$pad = 4 - ($payload.Length % 4); if ($pad -lt 4) { $payload += '=' * $pad }
[Text.Encoding]::UTF8.GetString([Convert]::FromBase64String($payload)) | ConvertFrom-Json
```

- Wyślij plik CSV przy użyciu curl (PowerShell — użyj `curl.exe`):

```powershell
curl.exe -v -X POST "http://localhost:8080/api/transactions/upload?user=Jacek" \
  -H "Authorization: Bearer $token" \
  -F "file=@C:/dev/smart-fin-analyzer/transactions_upload.csv;type=text/csv"
```

Jeśli odpowiedź to HTTP 200 — raport i rekordy powinny pojawić się w bazie.

### Manual audit smoke check po uploadzie CSV

Jeśli chcesz ręcznie potwierdzić, że upload przez `POST /api/transactions/upload?user=admin`
zapisał zarówno dane biznesowe, jak i audyt, sprawdź trzy rzeczy:
- rekord jest w `transactions`,
- rekord ma wpis w `transactions_aud`,
- `revtype = 0` oznacza insert,
- rewizja z `transactions_aud.rev` istnieje też w `revinfo`.

Najwygodniej użyć unikalnych `id` / `original_id` w CSV, np.:
- `MANUAL-AUD-001`
- `MANUAL-AUD-002`

1. Sprawdź rekordy biznesowe:

```powershell
docker exec -it smartfin-postgres psql -U finuser -d smartfin_db -c "SELECT original_id, amount, created_by FROM transactions WHERE original_id IN ('MANUAL-AUD-001','MANUAL-AUD-002') ORDER BY original_id;"
```

2. Sprawdź wpisy audytowe:

```powershell
docker exec -it smartfin-postgres psql -U finuser -d smartfin_db -c "SELECT original_id, rev, revtype FROM transactions_aud WHERE original_id IN ('MANUAL-AUD-001','MANUAL-AUD-002') ORDER BY original_id, rev;"
```

3. Sprawdź, czy rewizje z audytu istnieją w `revinfo`:

```powershell
docker exec -it smartfin-postgres psql -U finuser -d smartfin_db -c "SELECT COUNT(*) AS revinfo_rows FROM revinfo WHERE rev IN (SELECT rev FROM transactions_aud WHERE original_id IN ('MANUAL-AUD-001','MANUAL-AUD-002'));"
```

Interpretacja wyniku:
- jeśli rekordy są w `transactions`, upload zapisał dane biznesowe,
- jeśli dla tych samych `original_id` są wiersze w `transactions_aud`, audyt zadziałał,
- `revtype = 0` oznacza dodanie nowej transakcji,
- jeśli `revinfo_rows` jest równe liczbie rewizji z drugiego zapytania, powiązanie audytu z `revinfo` jest kompletne.

Uwaga dotycząca `/internal/debug-auth`:
- Endpoint `/internal/debug-auth` jest skonfigurowany jako publiczny (w `SecurityConfig` ścieżki `/internal/**` są `permitAll()`), więc wywołanie go nawet z nagłówkiem Authorization może zwrócić `anonymousUser` — jest to zamierzone, żeby endpoint był dostępny bez uwierzytelnienia w środowisku developerskim. Aby sprawdzić, czy token rzeczywiście daje rolę ADMIN, najlepiej:
  - sprawdzić payload tokena (powyższa metoda Base64), lub
  - wykonać chroniony endpoint (np. upload) i oglądać zachowanie (200 vs 403) albo włączyć logowanie Spring Security (DEBUG) i sprawdzić decyzję autoryzacyjną.


Postman Web — szybka diagnostyka kiedy plik nie jest wysyłany:
- Otwórz **Postman Console** (View → Show Postman Console). Wyślij request i sprawdź, czy w konsoli widać `form-data: file: <filename> (size: N bytes)`.
- Jeśli widzisz zamiast tego `file=@/path/to/file` lub `file: ""`, to plik nie został wybrany przez web client — zainstaluj Postman Agent lub użyj Desktop.

Jeśli chcesz, możesz dodać do repo instrukcję jak zainstalować Postman Desktop lub Agent — poniżej krótkie przypomnienie:

- Postman Desktop: https://www.postman.com/downloads/
- Postman Agent (dla web.postman.co): po zalogowaniu wybierz opcję „Install Postman Agent” i uruchom agenta lokalnie.

Ten fragment może być skopiowany do `scripts/Readme--Odpalanie-RESTów-z-Postmana.md` jeśli chcesz rozbudować sekcję o screeny i przykłady.

---

## Podsumowanie komend

| Co chcesz zrobić                          | Komenda                                                                   |
|-------------------------------------------|---------------------------------------------------------------------------|
| **[DEV]** Uruchomić tylko bazę            | `docker compose up -d db`                                                 |
| **[DEV]** Uruchomić aplikację (Gradle)    | `.\gradlew.bat runSmartFinDb -PappArgs="-u Jacek -f transactions_upload.csv"` |
| **[DOCKER]** Zbuduj obraz aplikacji       | `.\gradlew.bat jibDockerBuild`                                           |
| **[DOCKER]** Uruchomić bazę + aplikację   | `docker compose up -d`                                                    |
| **[DOCKER]** Przebuduj i uruchom          | `docker compose up --build -d`                                            |
| Uruchomić testy (automatyczny PG) z Flyway | `.\gradlew.bat "-Dspring.profiles.active=tc" "-Denable.flyway=true" clean test --no-daemon` |
| Uruchomić wszystkie testy w `local-pg` (pełny run) | `docker compose up -d db; powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\clean-db.ps1 -Mode local-pg -Force; .\gradlew.bat "-Dlocal.pg=true" "-Denable.flyway=true" clean test --no-daemon` |
| Uruchomić test z inspekcją bazy           | `.\gradlew.bat "-Dlocal.pg=true" test --tests "..."`                     |
| Uruchomić test z inspekcją bazy (z zachowaniem danych) | `.\gradlew.bat "-Dlocal.pg=true" "-Denable.flyway=true" "-Dlocal.pg.keepdata=true" test --tests "..." --no-daemon` |
| Sprawdzić dane w bazie produkcyjnej       | `docker exec -it smartfin-postgres psql -U finuser -d smartfin_db`        |
| Smoke check audytu po uploadzie CSV       | `docker exec -it smartfin-postgres psql -U finuser -d smartfin_db -c "SELECT original_id, rev, revtype FROM transactions_aud WHERE original_id IN ('...','...') ORDER BY original_id, rev;"` |
| Sprawdzić dane w bazie testowej           | `docker exec -it smartfin-postgres psql -U finuser -d smartfin_test`      |
| Zatrzymać bazę (zachowaj dane)            | `docker compose stop`                                                     |
| Zatrzymać bazę i usunąć dane              | `docker compose down -v`                                                  |

---

## 6. Dodatkowe informacje — migracje Flyway i helpery

- Migracje Flyway są w `src/main/resources/db/migration`; aktualny stan repo to **V1–V15**.
- W testach najlepiej uruchamiać migracje przez standardowe komendy z sekcji `## 2` (`-Denable.flyway=true`), bo pełna logika profili i cleanupu siedzi w `BaseIntegrationSpec`.
- Dla ręcznego sprawdzenia migracji poza testami jest helper `src/main/groovy/tools/RunFlyway.groovy` i task Gradle `runFlywayLocal`:
  ```powershell
  $env:FLYWAY_URL='jdbc:postgresql://localhost:5432/smartfin_test'
  $env:FLYWAY_USER='finuser'
  $env:FLYWAY_PASSWORD='finpass'
  .\gradlew.bat --no-daemon runFlywayLocal
  ```
- Helper uruchamia `migrate` na katalogu `src/main/resources/db/migration` i wypisuje podsumowanie zastosowanych migracji.
- Uwaga: helper `runFlywayLocal` ma w kodzie `baselineOnMigrate(true)`, więc traktuj go jako narzędzie pomocnicze do ręcznej inspekcji / awaryjnego uruchomienia migracji, a nie jako główne źródło prawdy dla standardowych runów aplikacji i testów.
