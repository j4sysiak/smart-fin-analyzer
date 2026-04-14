# Smart-Fin-Analyzer — Uruchamianie aplikacji i testów

Aplikacja: `src\main\groovy\pl\edu\praktyki\SmartFinDbApp.groovy`

```
┌────────────────────────────────────────────────────────────────────────────┐
│  CAŁY PROJEKT UŻYWA POSTGRESQL (brak H2!)                                 │
│                                                                            │
│  Produkcja  (bootRun / runSmartFinDb):  PostgreSQL via docker-compose      │
│             ├─ baza: smartfin_db,  port 5432,  finuser/finpass             │
│             └─ schemat: Hibernate ddl-auto=update                          │
│                                                                            │
│  Testy      (./gradlew test):           PostgreSQL via Docker CLI          │
│             ├─ tryb 'tc'       (domyślny) — auto-kontener, port 15432     │
│             └─ tryb 'local-pg' (inspekcja) — ręczny kontener, port 5432   │
└────────────────────────────────────────────────────────────────────────────┘
```

---

## 1. Produkcja — uruchomienie aplikacji z PostgreSQL

### Wymaganie: Docker Desktop musi być uruchomiony!

### Krok 1 — Uruchom bazę danych (docker-compose)

```powershell
cd C:\dev\smart-fin-analyzer
docker-compose up -d
```

To startuje kontener `smartfin-postgres` z:
- bazą `smartfin_db` (produkcja) i `smartfin_test` (testy local-pg, z `init-db.sql`)
- portem `5432`, użytkownikiem `finuser`, hasłem `finpass`
- wolumenem `postgres_data` (dane przetrwają restart kontenera)

Sprawdzenie, że kontener działa:
```powershell
docker ps --filter name=smartfin-postgres
docker exec smartfin-postgres pg_isready -U finuser
```

### Krok 2 — Uruchom aplikację

**Sposób 1: Gradle task `runSmartFinDb` (zalecany)**

```powershell
./gradlew runSmartFinDb -PappArgs="-u Jacek -f transactions_upload.csv"   # full wypas (import CSV)
./gradlew runSmartFinDb -PappArgs="-u Jacek"                              # bez importu CSV

./gradlew runSmartFinDb "-Denable.flyway=true" "-Dlogging.level.org.flywaydb=DEBUG" -PappArgs="-u Jacek"
```

Gdy zobaczysz `80% EXECUTING [39s]` — aplikacja działa:
```text
<==========---> 80% EXECUTING [39s]
> :runSmartFinDb
```

**Sposób 2: bootRun (ręcznie)**

```powershell
cd C:\dev\smart-fin-analyzer
.\gradlew.bat bootRun
```

> **Uwaga:** Jeśli pojawi się błąd o zablokowanym pliku — sprawdź procesy Javy:
> ```powershell
> Get-Process -Name java -ErrorAction SilentlyContinue | Format-Table Id,ProcessName,StartTime
> Stop-Process -Id <PID> -Force
> ```

### Parametry połączenia produkcyjnego (application.properties)

| Parametr       | Wartość                                            |
|----------------|----------------------------------------------------|
| Host:Port      | `localhost:5432`                                   |
| Baza           | `smartfin_db`                                      |
| User           | `finuser`                                          |
| Password       | `finpass`                                          |
| ddl-auto       | `update` (Hibernate tworzy/aktualizuje schemat)    |
| Flyway         | `enabled=true`, `baseline-on-migrate=true`         |

### Zatrzymanie

```powershell
# Zatrzymaj aplikację (Ctrl+C lub:)
Get-Process -Name java -ErrorAction SilentlyContinue | Format-Table Id,ProcessName,StartTime
Stop-Process -Id <PID> -Force

# Zatrzymaj bazę (dane zostaną w wolumenie postgres_data):
docker-compose stop

# LUB usuń kontener i dane:
docker-compose down -v
```




---

## 2. Testy — PostgreSQL w Dockerze (Docker CLI)

Testy **NIE** korzystają z docker-compose. 
`BaseIntegrationSpec` sam zarządza kontenerem.

Plik: `src/test/groovy/pl/edu/praktyki/BaseIntegrationSpec.groovy`

### Tryb domyślny: `tc` (automatyczny kontener)

```powershell
cd C:\dev\smart-fin-analyzer
```

Domyślnie (bez dodatkowych `-D`) testy uruchomią profil `tc`. W praktyce `BaseIntegrationSpec`
ustawia profil tak:
- jeśli podasz jawnie `-Dspring.profiles.active=...` — ta wartość ma pierwszeństwo,
- w przeciwnym razie, jeśli podasz `-Dlocal.pg=true` — profil zostanie ustawiony na `local-pg`,
- w przeciwnym razie zostanie wybrany `tc`.

Przykłady uruchomienia (PowerShell) — zwróć uwagę na cytowanie `-D`:

Bez niczego tryb domyślny (domyślnie `tc`):
```powershell
./gradlew.bat clean test
```

Jawnie `tc` (zalecane, gdy chcesz mieć pewność):
```powershell
./gradlew.bat "-Dspring.profiles.active=tc" test --tests "*CqrsSpec*"
./gradlew.bat "-Dspring.profiles.active=tc" test --tests "*UploadControllerSpec*"
./gradlew.bat "-Dspring.profiles.active=tc" test --tests "*RbacSpec*"
```

Z Flyway włączonym (debug):
```powershell
./gradlew.bat "-Dspring.profiles.active=tc" "-Denable.flyway=true" "-Dlogging.level.org.flywaydb=DEBUG" clean test --tests "*CqrsSpec*"
./gradlew.bat "-Dspring.profiles.active=tc" "-Denable.flyway=true" "-Dlogging.level.org.flywaydb=DEBUG" clean test --tests "*UploadControllerSpec*"
```

Możesz też ustawić `GRADLE_OPTS` aby uniknąć cytowania w każdej komendzie:
```powershell
$env:GRADLE_OPTS = "-Dspring.profiles.active=tc"
./gradlew.bat test --tests "*CqrsSpec*"
```

Dlaczego to ważne: bez cytowania `-D...` w PowerShell Gradle może potraktować je jako nazwę taska
(np. `.profiles.active=tc`) i zwrócić "Task '.profiles.active=tc' not found".

Co się dzieje pod spodem (tryb `tc`):
1. `docker rm -f smartfin-test-pg` — usuwa ewentualny stary kontener
2. `docker run -d --name smartfin-test-pg -p 15432:5432 ...` — startuje PostgreSQL 16
3. Czeka na `pg_isready` (max 30 s)
4. `@DynamicPropertySource` wstrzykuje URL `jdbc:postgresql://localhost:15432/testdb`
5. Hibernate tworzy schemat od zera (`ddl-auto=create`)
6. Po testach kontener zostaje — przy kolejnym uruchomieniu jest usuwany i tworzony od nowa

| Parametr   | Wartość                                       |
|------------|-----------------------------------------------|
| Host:Port  | `localhost:15432`                             |
| Baza       | `testdb`                                      |
| User       | `test`                                        |
| Password   | `test`                                        |
| ddl-auto   | `create` (schemat od zera przy każdym teście) |

> **Uwaga:** Tryb `tc` działa niezależnie od docker-compose. Kontener `smartfin-test-pg`
> (port 15432) nie koliduje z kontenerem `smartfin-postgres` (port 5432).

### Flyway (migracje) i profile testowe — aktualne uwagi

- Flyway jest włączony dla profili: `tc`, `local-pg` oraz `prod`. Migracje z
  `src/main/resources/db/migration` będą wykonywane automatycznie przy starcie aplikacji
  w tych profilach jeśli `spring.flyway.enabled=true` lub przy podaniu `-Denable.flyway=true`.
- Dla szybkich unit-testów korzystających z H2 Flyway pozostaje wyłączony (brak kompatybilności SQL
  między H2 a PostgreSQL i szybsze testy).
- Jeżeli chcesz wymusić uruchomienie Flyway w testach (np. sprawdzić nową migrację) dodaj
  `-Denable.flyway=true` przy uruchamianiu Gradle.

Przykład (PowerShell) — test `CqrsSpec` na profilu `tc` z Flyway w trybie debug:
```powershell
./gradlew.bat "-Dspring.profiles.active=tc" "-Denable.flyway=true" "-Dlogging.level.org.flywaydb=DEBUG" clean test --tests "*CqrsSpec*"
```

Po uruchomieniu sprawdź w logach Gradle wpisy Flyway: `Migrating schema` / `Successfully applied migration`.

Jak podejrzeć historię migracji (`flyway_schema_history`) w kontenerze testowym `smartfin-test-pg`:
```powershell
docker ps --filter "name=smartfin-test-pg"
docker exec -i smartfin-test-pg psql -U test -d testdb -c "SELECT version, description, installed_rank, success, installed_on FROM flyway_schema_history ORDER BY installed_rank;"
```

Uwaga: brak tabeli `flyway_schema_history` oznacza, że Flyway nie uruchomił się w tej instancji
(np. inny kontener/wyłączone `spring.flyway.enabled`). W takim wypadku sprawdź logi Gradle
i parametry uruchomienia (`-Denable.flyway=true`).




### Szybkie 3 kroki (co robić teraz) — uaktualnione

Aktualne odkrycia dotyczące `local-pg` i Flyway:
- `BaseIntegrationSpec` automatycznie ustawia profil (patrz wyżej) i przy `-Dlocal.pg=true`
  wykonuje automatyczne czyszczenie lokalnej bazy testowej. Obecna implementacja próbuje
  wykonać `DROP TABLE IF EXISTS ...` dla tabel testowych (transaction_entity_tags, transactions,
  counters, financial_summary) oraz `DROP SEQUENCE IF EXISTS tx_seq` przed uruchomieniem testów.
  To ma na celu zwiększenie deterministyczności uruchomień, ale nie usuwa obecnie tabeli
  `flyway_schema_history` — przez to mogą wystąpić konflikty/mismatche przy migracjach Flyway
  (opisane w sekcji "Problemy z Flyway przy local-pg").

Zalecane kroki:
1) Jeżeli chcesz deterministyczne, powtarzalne uruchomienia lokalne z Flyway — użyj
   trybu `tc` (domyślny) albo pozwól, aby `local-pg` przeprowadził pełne, destrukcyjne
   czyszczenie przed uruchomieniem (uwaga: utrata danych). Możliwości:
   - Opcja A (zalecana dla testów automatycznych): zawsze DROP tabel/sequence i
     także DROP TABLE IF EXISTS flyway_schema_history; po tym uruchom Flyway migrate.
     (destrukcyjne, ale gwarantuje, że migracje zostaną wykonane od początku)
   - Opcja B (inspekcja/bez kasowania): zachowaj dane i włącz `spring.flyway.baseline-on-migrate=true`
     w `application-local-pg.properties` aby Flyway nie próbował nadpisać istniejącego schematu —
     użyteczne gdy chcesz zachować ręcznie wprowadzone dane
   - Opcja C (długoterminowo): przerobić skrypty migracji na odporne (CREATE TABLE IF NOT EXISTS,
     warunkowe tworzenie sekwencji) — to zmienia semantykę migracji i wymaga przeglądu.

2) Jeżeli chcesz mieć kontrolę nad automatycznym czyszczeniem, możesz pominąć automatyczne
   czyszczenie dodając flagę `-Dlocal.pg.keepdata=true` (jeśli chcesz zachować dane do inspekcji).

3) Uruchom pełny zestaw testów z Flyway (tryb `tc`, niezawodne):
```powershell
cd C:\dev\smart-fin-analyzer
./gradlew.bat "-Dspring.profiles.active=tc" "-Denable.flyway=true" clean test
```

4) Uruchom pełny zestaw testów z `local-pg` i Flyway — przykładowo (uwaga: może wymagać
   ręcznego usunięcia `flyway_schema_history` jeśli automatyczne czyszczenie nie wystarcza):
```powershell
cd C:\dev\smart-fin-analyzer
./gradlew.bat "-Dlocal.pg=true" "-Denable.flyway=true" clean test --info
```

   Jeśli migracje się nie powiodą (np. `relation "counters" already exists` lub `relation "tx_seq" does not exist`)
   oznacza to, że lokalny schemat jest w stanie niezgodnym z oczekiwanym — patenty naprawy:
   - ręcznie DROP tabel i `flyway_schema_history` w `smartfin-postgres` i powtórzyć testy
   - lub ustawić `-Dlocal.pg.keepdata=true` i rozwiązać problem ręcznie

5) Helper: możesz nadal używać `scripts/run-integration-tests-with-flyway.ps1` dla trybu `tc` —
   on uruchamia testy i potem wypisuje `flyway_schema_history` z kontenera `smartfin-test-pg`.





### Tryb inspekcji: `local-pg` (ręczny kontener)

Użyj tego trybu, gdy chcesz **podejrzeć dane w bazie po teście** (psql, DBeaver, pgAdmin).
Schemat jest aktualizowany (`ddl-auto=update`), a dane **zostają** po teście.

Ważne: jeżeli uruchomisz test z `-Dlocal.pg=true`, `BaseIntegrationSpec` automatycznie ustawi
`spring.profiles.active=local-pg` (o ile nie podałeś jawnie `-Dspring.profiles.active=...`).
Dodatkowo spróbuje automatycznie wyczyścić tabelki testowe (TRUNCATE) przed uruchomieniem,
żeby testy były bardziej deterministyczne. Mechanizm próbuje najpierw `docker exec smartfin-postgres ...`,
a następnie lokalny klient `psql` (jeśli jest dostępny). Jeśli oba podejścia zawiodą otrzymasz
instrukcję jak wyczyścić bazę ręcznie.

Możesz użyć kontenera z docker-compose (ten sam!) — baza `smartfin_test` jest tam tworzona przez `init-db.sql`.

Krok 1 — upewnij się, że docker-compose działa lub uruchom kontener ręcznie:
```powershell
docker-compose up -d
docker exec smartfin-postgres pg_isready -U finuser

# LUB (jeśli nie używasz docker-compose):
docker run -d --name smartfin-postgres `
  -e POSTGRES_DB=smartfin_test `
  -e POSTGRES_USER=finuser `
  -e POSTGRES_PASSWORD=finpass `
  -p 5432:5432 `
  postgres:16-alpine
```

Krok 2 — uruchom wybrany test z flagą `-Dlocal.pg=true`:
```powershell
.\gradlew.bat "-Dlocal.pg=true" test --tests "pl.edu.praktyki.repository.IntegrationDbSpec"
# lub
.\gradlew.bat "-Dlocal.pg=true" test --tests "*CqrsSpec*"
.\gradlew.bat "-Dlocal.pg=true" test --tests "*UploadControllerSpec*"
```

Jeżeli chcesz wymusić inny profil mimo `-Dlocal.pg=true`, podaj jawnie `-Dspring.profiles.active=...` przed taskiem.

Krok 3 — podejrzyj dane w bazie (po zakończeniu testu):
```powershell
docker exec -it smartfin-postgres psql -U finuser -d smartfin_test
```
```sql
SELECT * FROM transactions LIMIT 10;
\dt           -- lista tabel
\q            -- wyjście
```

Krok 4 — posprzątaj (gdy skończysz):
```powershell
docker-compose stop          # jeśli używasz docker-compose
# LUB:
docker stop smartfin-postgres; docker rm smartfin-postgres
```

| Parametr   | Wartość                                           |
|------------|---------------------------------------------------|
| Host:Port  | `localhost:5432`                                  |
| Baza       | `smartfin_test`                                   |
| User       | `finuser`                                         |
| Password   | `finpass`                                         |
| ddl-auto   | `update` (dane zostają po teście do inspekcji)    |

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
- Rozwiń sekcję **auth-controller** → `GET /auth/token`
- Kliknij **Try it out** → **Execute`
- Skopiuj wartość `token` z odpowiedzi (sam ciąg `eyJ...`, bez cudzysłowów)

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

### Wysyłanie plików (multipart/form-data) — ważne

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
$token = (Invoke-RestMethod -Uri "http://localhost:8080/auth/token?user=admin" -Method Get).token
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
| Uruchomić bazę (produkcja)                | `docker-compose up -d`                                                    |
| Uruchomić aplikację                       | `./gradlew runSmartFinDb -PappArgs="-u Jacek -f transakcje.csv"`          |
| Uruchomić testy (automatyczny PG) z Flyway | `./gradlew.bat "-Dspring.profiles.active=tc" "-Denable.flyway=true" clean test` |
| Uruchomić test z inspekcją bazy           | `./gradlew.bat "-Dlocal.pg=true" test --tests "..."`                     |
| Sprawdzić dane w bazie produkcyjnej       | `docker exec -it smartfin-postgres psql -U finuser -d smartfin_db`        |
| Sprawdzić dane w bazie testowej           | `docker exec -it smartfin-postgres psql -U finuser -d smartfin_test`      |
| Zatrzymać bazę (zachowaj dane)            | `docker-compose stop`                                                     |
| Zatrzymać bazę i usunąć dane              | `docker-compose down -v`                                                  |
