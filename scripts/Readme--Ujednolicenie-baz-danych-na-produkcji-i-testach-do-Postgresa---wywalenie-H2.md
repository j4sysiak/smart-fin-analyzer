Lab72
-----

Lab72 — Ujednolicenie baz danych na produkcji i testach do Postgresa — wywalenie H2
====================================================================================

> **TL;DR:** Eliminujemy H2 z całego projektu. Produkcja, testy automatyczne (`tc`)
> i testy inspekcyjne (`local-pg`) — wszystko jedzie na **PostgreSQL 16**.
> Testcontainers API zastępujemy **Docker CLI** (`"docker run".execute()`),
> bo `docker-java` nie działa z Docker Desktop ≥ 4.67.

```
┌─────────────────────────────────────────────────────────────────────┐
│                    PRZED (bałagan)                                  │
│                                                                     │
│  Produkcja:  PostgreSQL (docker-compose) ← ale README mówił "H2"   │
│  Testy:      Testcontainers API          ← crashuje na DD 4.67+    │
│  Logi:       "Uruchamianie wersji z BAZĄ DANYCH (H2)..."           │
│  Konfig:     ddl-auto=validate           ← wysypuje się na start   │
│  build.gradle: zależności H2 + Testcontainers  ← nieużywane/zepsute│
│                                                                     │
├─────────────────────────────────────────────────────────────────────┤
│                    PO (porządek)                                     │
│                                                                     │
│  Produkcja:  PostgreSQL (docker-compose, port 5432)                 │
│  Testy tc:   PostgreSQL (Docker CLI, port 15432) ← automatyczny    │
│  Testy local-pg: PostgreSQL (docker-compose, port 5432) ← inspekcja│
│  Logi:       "Uruchamianie wersji z BAZĄ DANYCH (PostgreSQL)..."   │
│  Konfig:     ddl-auto=update, Flyway baseline-on-migrate           │
│  build.gradle: tylko org.postgresql:postgresql                      │
│                                                                     │
│  Wynik: 108 testów ✓  BUILD SUCCESSFUL  ~3-4 min                   │
└─────────────────────────────────────────────────────────────────────┘
```

---

## 1. Problem — co było nie tak?

### 1.1. Rozbieżność dokumentacji i kodu

Plik `application.properties` już wskazywał na PostgreSQL (`jdbc:postgresql://localhost:5432/smartfin_db`),
ale stary README twierdził, że produkcja używa H2. Log w `SmartFinDbApp.groovy` wypisywał:

```
>>> Uruchamianie wersji z BAZĄ DANYCH (H2)...
```

Programista uruchamiający projekt po raz pierwszy dostawał `PSQLException: Connection refused` — bo
nie wiedział, że musi najpierw odpalić `docker-compose up -d`.

### 1.2. Testcontainers API nie działa z Docker Desktop ≥ 4.67

Stary `BaseIntegrationSpec` używał biblioteki Testcontainers:

```groovy
// ❌ STARY KOD — nie działa z Docker Desktop 4.67+ (API 1.54)
@Testcontainers
@SpringBootTest
class BaseIntegrationSpec extends Specification {
    @Container
    static PostgreSQLContainer pg = new PostgreSQLContainer("postgres:16-alpine")
}
```

Biblioteka `docker-java` (wewnątrz Testcontainers) wysyła request do Docker Engine API
i dostaje `BadRequestException: Status 400` z pustym JSON-em:

```
com.github.dockerjava.api.exception.BadRequestException: {"message":""}
```

To jest znany problem niekompatybilności `docker-java` z najnowszymi wersjami Docker Desktop
(API 1.54+). Nie ma na to szybkiego fix-a poza czekaniem na aktualizację Testcontainers.

### 1.3. `ddl-auto=validate` wysypywał się na pierwszym uruchomieniu

Produkcyjna konfiguracja miała `spring.jpa.hibernate.ddl-auto=validate` — Hibernate sprawdzał
schemat bazy, ale na świeżym kontenerze (bez tabel) od razu rzucał wyjątek.

### 1.4. Zakomentowane relikty H2

W `application.properties` zostawały dziesiątki zakomentowanych linii o H2 (konsola, URL pliku,
opis ddl-auto), a w `build.gradle` wisiały zależności Testcontainers (`spock:1.19.3`,
`postgresql:1.19.3`, `jdbc:1.19.3`), których nikt nie używał.

---

## 2. Rozwiązanie — co zmieniliśmy

### 2.1. Produkcja — `application.properties`

Aktywna konfiguracja (linie 1–30):

```properties
# DATASOURCE (POSTGRESQL IN DOCKER PORT 5432)
spring.datasource.url=jdbc:postgresql://localhost:5432/smartfin_db
spring.datasource.username=finuser
spring.datasource.password=finpass
spring.datasource.driverClassName=org.postgresql.Driver

# JPA / HIBERNATE
spring.jpa.hibernate.ddl-auto=update       # ← było: validate (crashowało)

# FLYWAY
spring.flyway.enabled=true
spring.flyway.baseline-on-migrate=true     # ← adoptuje istniejący schemat
```

Zakomentowana sekcja H2 została z adnotacją `# Wywalam H2 - Look at: Lab72` — jako
dokumentacja historyczna (czemu nie usuwamy? bo komentarz wyjaśnia decyzję).

**Zmiana w `SmartFinDbApp.groovy`** (linia 71):
```groovy
// PRZED:
println ">>> Uruchamianie wersji z BAZĄ DANYCH (H2)..."
// PO:
println ">>> Uruchamianie wersji z BAZĄ DANYCH (PostgreSQL)..."
```

### 2.2. docker-compose.yml — kontener produkcyjny

```yaml
services:
  db:
    image: postgres:16-alpine
    container_name: smartfin-postgres
    ports:
      - "5432:5432"
    environment:
      POSTGRES_USER: finuser
      POSTGRES_PASSWORD: finpass
      POSTGRES_DB: smartfin_db              # ← główna baza (produkcja)
    volumes:
      - ./postgres_data:/var/lib/postgresql/data
      - ./scripts/init-db.sql:/docker-entrypoint-initdb.d/init-db.sql
```

Skrypt `scripts/init-db.sql` tworzy dodatkową bazę `smartfin_test` (używaną przez tryb `local-pg`):

```sql
CREATE DATABASE smartfin_test;
```

Katalog `postgres_data/` dodany do `.gitignore` — surowe pliki PG nie trafiają do repozytorium.

### 2.3. Testy — nowy BaseIntegrationSpec (Docker CLI zamiast Testcontainers)

To jest serce tej migracji. Zamiast Testcontainers API, `BaseIntegrationSpec` uruchamia
kontener PostgreSQL bezpośrednio przez Docker CLI — poleceniem `"docker run ...".execute()`.

Plik: `src/test/groovy/pl/edu/praktyki/BaseIntegrationSpec.groovy`

**Dwa tryby pracy:**

| Cecha            | Tryb `tc` (domyślny)                    | Tryb `local-pg` (inspekcja)             |
|------------------|-----------------------------------------|-----------------------------------------|
| **Aktywacja**    | `./gradlew test`                        | `./gradlew test -Dlocal.pg=true`        |
| **Kontener**     | automatyczny (`smartfin-test-pg`)       | ręczny (docker-compose / `docker run`)  |
| **Port**         | `15432`                                 | `5432`                                  |
| **Baza**         | `testdb`                                | `smartfin_test`                         |
| **User/Pass**    | `test` / `test`                         | `finuser` / `finpass`                   |
| **ddl-auto**     | `create` (schemat od zera)              | `update` (dane zostają)                 |
| **Po teście**    | kontener zostaje (usunięty przy kolejnym run) | dane do inspekcji (psql/DBeaver) |

**Kluczowy fragment — uruchamianie kontenera Docker CLI:**

```groovy
private static void startPostgresContainer() {
    // 1. Usuń ewentualny stary kontener
    runCmd("docker rm -f $CONTAINER_NAME")

    // 2. Uruchom nowy kontener PostgreSQL
    def startCmd = "docker run -d --name $CONTAINER_NAME " +
            "-p ${PG_PORT}:5432 " +
            "-e POSTGRES_DB=$PG_DB " +
            "-e POSTGRES_USER=$PG_USER " +
            "-e POSTGRES_PASSWORD=$PG_PASS " +
            "postgres:16-alpine"
    def result = runCmd(startCmd)
    if (result != 0) {
        throw new RuntimeException("Nie udało się uruchomić kontenera PostgreSQL!")
    }

    // 3. Czekaj aż PostgreSQL będzie gotowy (max 30 sekund)
    def ready = false
    for (int i = 0; i < 30; i++) {
        def check = runCmd("docker exec $CONTAINER_NAME pg_isready -U $PG_USER")
        if (check == 0) { ready = true; break }
        Thread.sleep(1000)
    }
    if (!ready) {
        throw new RuntimeException("PostgreSQL nie uruchomił się w ciągu 30 sekund!")
    }
}
```

**Kluczowy fragment — `@DynamicPropertySource` (wstrzykiwanie URL):**

```groovy
@DynamicPropertySource
static void configureDataSource(DynamicPropertyRegistry registry) {
    if (LOCAL_PG) {
        // Tryb inspekcji: ręczny kontener na localhost:5432
        registry.add("spring.datasource.url",
                () -> "jdbc:postgresql://localhost:${LOCAL_PG_PORT}/${LOCAL_PG_DB}")
        registry.add("spring.datasource.username", () -> LOCAL_PG_USER)
        registry.add("spring.datasource.password", () -> LOCAL_PG_PASS)
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "update")
    } else {
        // Tryb automatyczny: Docker CLI na localhost:15432
        registry.add("spring.datasource.url",
                () -> "jdbc:postgresql://localhost:${PG_PORT}/${PG_DB}")
        registry.add("spring.datasource.username", () -> PG_USER)
        registry.add("spring.datasource.password", () -> PG_PASS)
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create")
    }
    // Wspólne — wyłączamy Flyway i schedulery w testach
    registry.add("spring.datasource.driverClassName", () -> "org.postgresql.Driver")
    registry.add("spring.flyway.enabled", () -> "false")
    registry.add("app.scheduling.enabled", () -> "false")
}
```

**Dlaczego Docker CLI zamiast Testcontainers?**

| Testcontainers API                             | Docker CLI (`"docker run".execute()`)       |
|------------------------------------------------|---------------------------------------------|
| ❌ `docker-java` → BadRequestException na DD 4.67+ | ✅ Działa z każdą wersją Docker Desktop |
| ❌ Czarna skrzynka — trudno debugować           | ✅ Widać dokładnie co się dzieje            |
| ❌ Dodatkowe zależności JAR                      | ✅ Zero zależności (Groovy `execute()`)     |
| ✅ Ładne API, automatyczny lifecycle            | ⚠️ Ręczne zarządzanie kontenerem           |

Wybieramy pragmatyzm: skoro `docker-java` nie działa, a `docker run` tak — używamy tego, co działa.

### 2.4. Profile testowe — 3 pliki konfiguracyjne

```
src/test/resources/
├── application.properties           ← fallback (Flyway off, scheduling off)
├── application-tc.properties        ← profil 'tc' (domyślny, port 15432)
└── application-local-pg.properties  ← profil 'local-pg' (port 5432, ddl-auto=update)
```

Tabela porównawcza:

| Właściwość               | `application.properties` (test) | `application-tc.properties` | `application-local-pg.properties` |
|--------------------------|---------------------------------|-----------------------------|-----------------------------------|
| `spring.flyway.enabled`  | `false`                         | `false`                     | `false`                           |
| `app.scheduling.enabled` | `false`                         | `false`                     | `false`                           |
| `spring.jpa.show-sql`    | –                               | `false`                     | `true`                            |
| `ddl-auto`               | (z `@DynamicPropertySource`)    | (z `@DynamicPropertySource`)| `update`                          |
| Datasource URL           | (z `@DynamicPropertySource`)    | (z `@DynamicPropertySource`)| `localhost:5432/smartfin_test`    |

> **Uwaga:** `@DynamicPropertySource` w `BaseIntegrationSpec` nadpisuje wartości z plików properties
> — dzięki temu jeden plik `.groovy` kontroluje oba tryby.

### 2.5. build.gradle — czyszczenie zależności

**Usunięte:**
```groovy
// ❌ Te zależności zostały usunięte:
// testImplementation 'org.testcontainers:spock:1.19.3'
// testImplementation 'org.testcontainers:postgresql:1.19.3'
// testImplementation 'org.testcontainers:jdbc:1.19.3'
// runtimeOnly 'com.h2database:h2'
```

**Zostaje (jedyna zależność do bazy):**
```groovy
// ✅ Sterownik JDBC PostgreSQL — wymagany do połączenia z PostgreSQL
implementation 'org.postgresql:postgresql'
```

Komentarz w `build.gradle` wyjaśnia powód:
```groovy
// UWAGA: Testcontainers docker-java nie jest kompatybilny z Docker Desktop 4.67+ (API 1.54)
// Zamiast Testcontainers używamy Docker CLI bezpośrednio w BaseIntegrationSpec.
```

### 2.6. Konfiguracja Gradle — przekazywanie flagi `-Dlocal.pg=true`

W bloku `test` w `build.gradle` dodaliśmy jedną kluczową linię:

```groovy
test {
    useJUnitPlatform()
    // Przekazuje -Dlocal.pg=true z CLI Gradle'a do JVM testowej
    systemProperty 'local.pg', System.getProperty('local.pg')
}
```

Bez tego `System.getProperty("local.pg")` w `BaseIntegrationSpec` zawsze zwracałby `null`,
bo Gradle uruchamia testy w oddzielnym procesie JVM.

---

## 3. Bug-fixy przy okazji

### 3.1. `DailyReportScheduler` — NPE/AmbiguousMethodException na pustej bazie

**Problem:** Scheduler co 10s wywoływał `allTx*.amountPLN.sum()`. Gdy transakcja miała
`amountPLN = null`, Groovy dostawał `Ambiguous method overloading for BigDecimal#plus` —
bo `BigDecimal.plus(null)` pasuje do wielu wariantów (`Character`, `String`, `Number`...).

**Fix:**
```groovy
// PRZED (crashowało):
def totalPln = allTx*.amountPLN.sum()

// PO (bezpieczne):
def amounts = allTx*.amountPLN.findAll { it != null }
def totalPln = amounts ? amounts.sum() : BigDecimal.ZERO
```

### 3.2. Swagger UI — brak autoryzacji JWT (403 Forbidden)

**Problem:** Swagger UI ładował się poprawnie, ale każdy request do `/api/transactions`
zwracał `403 Forbidden`, bo nie dołączał tokena JWT.

**Fix:** Nowy plik `config/OpenApiConfig.groovy`:
```groovy
@Configuration
class OpenApiConfig {
    @Bean
    OpenAPI smartFinOpenAPI() {
        final String schemeName = 'bearerAuth'
        return new OpenAPI()
                .info(new Info().title('Smart-Fin-Analyzer API').version('1.0'))
                .addSecurityItem(new SecurityRequirement().addList(schemeName))
                .components(new Components()
                        .addSecuritySchemes(schemeName, new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme('bearer')
                                .bearerFormat('JWT')))
    }
}
```

Teraz Swagger UI ma przycisk **🔒 Authorize** — wklejasz token z `GET /auth/token?user=dev`
i wszystkie requesty automatycznie dodają nagłówek `Authorization: Bearer <token>`.

---

## 4. Architektura portów i kontenerów

```
                        DOCKER DESKTOP
┌───────────────────────────────────────────────────────────┐
│                                                           │
│  ┌─────────────────────────────────┐                      │
│  │  smartfin-postgres              │   docker-compose     │
│  │  (postgres:16-alpine)           │                      │
│  │  Port: 5432                     │                      │
│  │                                 │                      │
│  │  ├── smartfin_db   (produkcja)  │                      │
│  │  └── smartfin_test (local-pg)   │                      │
│  └─────────────────────────────────┘                      │
│                                                           │
│  ┌─────────────────────────────────┐                      │
│  │  smartfin-test-pg               │   Docker CLI (auto)  │
│  │  (postgres:16-alpine)           │                      │
│  │  Port: 15432                    │                      │
│  │                                 │                      │
│  │  └── testdb        (testy tc)   │                      │
│  └─────────────────────────────────┘                      │
│                                                           │
└───────────────────────────────────────────────────────────┘
        ▲                       ▲
        │                       │
   ./gradlew                ./gradlew
   runSmartFinDb            clean test
   (port 5432)              (port 15432)
```

Oba kontenery **nie kolidują** — różne nazwy, różne porty, różne bazy.

---

## 5. Jak uruchomić

### Produkcja
```powershell
docker-compose up -d
./gradlew runSmartFinDb -PappArgs="-u Jacek -f transakcje.csv"
# Aplikacja na http://localhost:8080
# Swagger:   http://localhost:8080/swagger-ui/index.html
```

### Testy — tryb `tc` (domyślny, automatyczny kontener)
```powershell
./gradlew clean test
# Kontener smartfin-test-pg startuje sam na porcie 15432
# 108 testów, ~3-4 minuty
```

### Testy — tryb `local-pg` (inspekcja danych po teście)
```powershell
docker-compose up -d
./gradlew test --tests "pl.edu.praktyki.repository.IntegrationDbSpec" "-Dlocal.pg=true"
# Dane zostają w bazie smartfin_test — podejrzyj:
docker exec -it smartfin-postgres psql -U finuser -d smartfin_test
```

---

## 6. Wyniki

```
BUILD SUCCESSFUL in 3m 38s
5 actionable tasks: 3 executed, 2 up-to-date

108 testów Spock ✓
0 failures
0 skipped
```

- ✅ Zero H2 w całym projekcie
- ✅ PostgreSQL 16 na wszystkich środowiskach
- ✅ Docker CLI zamiast Testcontainers API (workaround na bug docker-java)
- ✅ Dwa tryby testowania (automatyczny + inspekcja)
- ✅ Swagger UI z autoryzacją JWT (przycisk Authorize)
- ✅ DailyReportScheduler odporny na nulle

---

## 7. Lista zmienionych plików

| Plik                                          | Co zmieniono                                              |
|-----------------------------------------------|-----------------------------------------------------------|
| `application.properties`                      | `ddl-auto=validate` → `update`, aktywna konfiguracja PG  |
| `SmartFinDbApp.groovy`                        | Log `"H2"` → `"PostgreSQL"`                              |
| `BaseIntegrationSpec.groovy`                  | Cały plik — Docker CLI zamiast Testcontainers API         |
| `build.gradle`                                | Usunięto H2 i Testcontainers, zostawiono `postgresql`     |
| `application-tc.properties`                   | Nowy profil testowy (tryb automatyczny)                   |
| `application-local-pg.properties`             | Nowy profil testowy (tryb inspekcji)                      |
| `docker-compose.yml`                          | PostgreSQL 16-alpine + init-db.sql                        |
| `scripts/init-db.sql`                         | Tworzenie bazy `smartfin_test`                            |
| `.gitignore`                                  | Dodano `postgres_data/`                                   |
| `DailyReportScheduler.groovy`                 | Fix: filtrowanie nulli w `.sum()`                         |
| `config/OpenApiConfig.groovy`                 | Nowy: konfiguracja Swagger z JWT (przycisk Authorize)     |
| `web/AuthController.groovy`                   | Adnotacje `@Operation`, `@SecurityRequirements`           |

---

## 8. Dlaczego to ważne? (Senior perspective)

**Parity środowisk (Dev/Test/Prod):**
Jedna z [12-Factor App](https://12factor.net/dev-prod-parity) zasad mówi: "Keep development,
staging, and production as similar as possible". H2 w testach + PostgreSQL na produkcji
to klasyczny anty-wzorzec — różnice w SQL dialektach, typach danych i zachowaniu mogą
powodować błędy widoczne dopiero na produkcji.

**Pragmatyczny workaround:**
Zamiast czekać na fix w `docker-java` / Testcontainers, zastosowaliśmy Docker CLI.
To pokazuje umiejętność diagnozowania problemów infrastrukturalnych i znajdowania
obejść — cechy cenione u programistów pracujących z CI/CD.

**Dwa tryby testowania:**
- `tc` (automatyczny) — dla CI/CD i codziennej pracy (zero konfiguracji)
- `local-pg` (inspekcja) — dla debugowania (dane zostają do podglądu)

To wzorzec stosowany w dojrzałych projektach, gdzie deweloper może płynnie
przełączać się między "szybkim testem" a "głębokim debugowaniem".
