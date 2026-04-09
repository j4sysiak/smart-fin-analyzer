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
./gradlew runSmartFinDb -PappArgs="-u Jacek -f transakcje.csv"   # full wypas (import CSV)
./gradlew runSmartFinDb -PappArgs="-u Jacek"                     # bez importu CSV
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

Testy **NIE** korzystają z docker-compose. `BaseIntegrationSpec` sam zarządza kontenerem.

Plik: `src/test/groovy/pl/edu/praktyki/BaseIntegrationSpec.groovy`

### Tryb domyślny: `tc` (automatyczny kontener)

```powershell
cd C:\dev\smart-fin-analyzer
./gradlew clean test
```

Co się dzieje pod spodem:
1. `docker rm -f smartfin-test-pg` — usuwa ewentualny stary kontener
2. `docker run -d --name smartfin-test-pg -p 15432:5432 ...` — startuje PostgreSQL 16
3. Czeka na `pg_isready` (max 30 s)
4. `@DynamicPropertySource` wstrzykuje URL `jdbc:postgresql://localhost:15432/testdb`
5. Hibernate tworzy schemat od zera (`ddl-auto=create`)
6. Po testach kontener **zostaje** — Spring zamyka HikariPool czysto
7. Przy następnym `./gradlew test` kontener jest usuwany i tworzony od nowa

| Parametr   | Wartość                                       |
|------------|-----------------------------------------------|
| Host:Port  | `localhost:15432`                             |
| Baza       | `testdb`                                      |
| User       | `test`                                        |
| Password   | `test`                                        |
| ddl-auto   | `create` (schemat od zera przy każdym teście) |

> **Uwaga:** Tryb `tc` działa niezależnie od docker-compose. Kontener `smartfin-test-pg`
> (port 15432) nie koliduje z kontenerem `smartfin-postgres` (port 5432).





### Tryb inspekcji: `local-pg` (ręczny kontener)

Użyj tego trybu, gdy chcesz **podejrzeć dane w bazie po teście** (psql, DBeaver, pgAdmin).
Schemat jest aktualizowany (`ddl-auto=update`), a dane **zostają** po teście.

Możesz użyć kontenera z docker-compose (ten sam!) — baza `smartfin_test` jest tam tworzona przez `init-db.sql`.

**Krok 1 — upewnij się, że docker-compose działa:**
```powershell
docker-compose up -d
docker exec smartfin-postgres pg_isready -U finuser
```

Albo uruchom osobny kontener:
```powershell
docker run -d --name smartfin-postgres `
  -e POSTGRES_DB=smartfin_test `
  -e POSTGRES_USER=finuser `
  -e POSTGRES_PASSWORD=finpass `
  -p 5432:5432 `
  postgres:16-alpine
```

**Krok 2 — uruchom wybrany test z flagą `-Dlocal.pg=true`:**
```powershell
./gradlew test --tests "pl.edu.praktyki.repository.IntegrationDbSpec" "-Dlocal.pg=true"
```

**Krok 3 — podejrzyj dane w bazie:**
```powershell
docker exec -it smartfin-postgres psql -U finuser -d smartfin_test
```
```sql
SELECT * FROM transactions LIMIT 10;
\dt           -- lista tabel
\q            -- wyjście
```

**Krok 4 — posprzątaj (gdy skończysz):**
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
- Kliknij **Try it out** → **Execute**
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

---

## Podsumowanie komend

| Co chcesz zrobić                          | Komenda                                                                   |
|-------------------------------------------|---------------------------------------------------------------------------|
| Uruchomić bazę (produkcja)                | `docker-compose up -d`                                                    |
| Uruchomić aplikację                       | `./gradlew runSmartFinDb -PappArgs="-u Jacek -f transakcje.csv"`          |
| Uruchomić testy (automatyczny PG)         | `./gradlew clean test`                                                    |
| Uruchomić test z inspekcją bazy           | `./gradlew test --tests "..." "-Dlocal.pg=true"`                          |
| Sprawdzić dane w bazie produkcyjnej       | `docker exec -it smartfin-postgres psql -U finuser -d smartfin_db`        |
| Sprawdzić dane w bazie testowej           | `docker exec -it smartfin-postgres psql -U finuser -d smartfin_test`      |
| Zatrzymać bazę (zachowaj dane)            | `docker-compose stop`                                                     |
| Zatrzymać bazę i usunąć dane              | `docker-compose down -v`                                                  |
