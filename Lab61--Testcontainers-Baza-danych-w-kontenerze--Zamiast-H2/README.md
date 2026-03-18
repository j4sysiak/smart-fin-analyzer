Lab 61
------

Lab61--Testcontainers-Baza-danych-w-kontenerze--Zamiast-H2
----------------------------------------------------------

Zaczynamy realizację Etapu 1: Kuloodporne Testy i Izolacja.

Mamy już za sobą WireMock (Lab61), więc teraz przechodzimy do "ciężkiej artylerii" testowania integracyjnego: Testcontainers.

Problem: 
Testowanie na bazie H2 (in-memory) to nie to samo, co baza PostgreSQL na produkcji. 
H2 ma inne dialekty SQL, inną obsługę typów danych i inną wydajność. 
Możesz napisać test, który przechodzi na H2, a po wdrożeniu na produkcję (PostgreSQL) wywali się z błędem składni SQL.

Rozwiązanie:
Testcontainers uruchamia prawdziwego PostgreSQL w kontenerze Dockerowym, 
tylko na czas trwania testów. 
Dzięki temu testujesz dokładnie taką bazę, jakiej użyjesz na produkcji.

1. Zależności (build.gradle)

Musisz mieć zainstalowanego Dockera na maszynie. 
Dodaj to do dependencies:

```groovy
dependencies {
// ... poprzednie
    
    // Testcontainers do testowania integracyjnego z prawdziwą bazą danych PostgreSQL
    // Lab61--Testowanie-Integracyjne-z-Testcontainers--Groovy-Spock-and-Docker
    testImplementation 'org.testcontainers:spock:1.19.3'
    testImplementation 'org.testcontainers:postgresql:1.19.3'
    testImplementation 'org.testcontainers:jdbc:1.19.3'

    // Sterownik JDBC PostgreSQL — wymagany przez Testcontainers do połączenia z kontenerem
    testRuntimeOnly 'org.postgresql:postgresql'
}
```

2. Konfiguracja application-test.properties

Tworzymy 3 profile: 
 - `test` (dla testów), 
 - `local-pg` (dla lokalnego Postgresa)
 - `tc` (dla Testcontainers) - główna konfiguracja do testów integracyjnych.


1. opcja `local-pg` (dla tych, którzy chcą ręcznie połączyć się z lokalnym Postgresem, np. do inspekcji danych):

```
# ==========================================
# PROFIL: local-pg — Ręczny kontener PostgreSQL do inspekcji
# ==========================================
# Użycie: docker run -d --name smartfin-postgres \
#   -e POSTGRES_DB=smartfin_db -e POSTGRES_USER=smartfin -e POSTGRES_PASSWORD=smartfin123 \
#   -p 5432:5432 postgres:16-alpine
#
# Połączenie z psql:
#   docker exec -it smartfin-postgres psql -U smartfin -d smartfin_db

spring.datasource.url=jdbc:postgresql://localhost:5432/smartfin_db
spring.datasource.username=smartfin
spring.datasource.password=smartfin123
spring.datasource.driverClassName=org.postgresql.Driver

spring.flyway.enabled=false
spring.jpa.hibernate.ddl-auto=create
spring.jpa.show-sql=true
spring.h2.console.enabled=false
```


2. opcja `tc` jest "magiczna" — mówi Springowi, żeby przed testami odpalił kontener z Postgresem i połączył się z nim.
```
# ==========================================
# KONFIGURACJA TESTOWA - TESTCONTAINERS (POSTGRESQL)
# ==========================================

# 1. Magiczny URL: 'tc' w środku mówi Springowi: "Zanim się połączysz, odpal kontener z Postgresem w Dockerze!"
spring.datasource.url=jdbc:tc:postgresql:16-alpine:///smartfin_db
spring.datasource.driverClassName=org.testcontainers.jdbc.ContainerDatabaseDriver

# 2. Zarządzanie schematem
spring.flyway.enabled=false
spring.jpa.hibernate.ddl-auto=create-drop

# 3. Logi
spring.jpa.show-sql=true

# 4. Upewniamy się, że konsola H2 jest wyłączona
spring.h2.console.enabled=false
```

3. opcja `test` (dla testów) dla  H2 pozostaje bez zmian, ale jest tam dla kompletności:
```
# ==========================================================
# DOMYŚLNA KONFIGURACJA TESTOWA (H2 in-memory) - nie plikowa
# ==========================================================
spring.datasource.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1
spring.datasource.driverClassName=org.h2.Driver
spring.datasource.username=sa
spring.datasource.password=

spring.flyway.enabled=false
spring.jpa.hibernate.ddl-auto=create-drop
spring.jpa.show-sql=true

spring.h2.console.enabled=false
spring.main.allow-bean-definition-overriding=true
```

i jak teraz testujemy?
W testach integracyjnych (Spock) używamy profilu `tc`, który automatycznie odpala Testcontainers z PostgreSQL.
polecenie do uruchomienia testów:
`./gradlew test --tests "pl.edu.praktyki.repository.IntegrationDbSpec"`

pozostawiamy `local-pg` do ręcznego testowania i inspekcji danych w kontenerze PostgreSQL, jeśli ktoś chce.
polecenia do zarządzania lokalnym kontenerem PostgreSQL:
- Uruchomienie:
`docker run -d --name smartfin-postgres -e POSTGRES_DB=smartfin
- Użytkownik: smartfin, hasło: smartfin123 -p 5432:5432 postgres:16-alpine`
- Połączenie z psql:
`docker exec -it smartfin-postgres psql -U smartfin -d smartfin_db`
- Zatrzymanie i usunięcie kontenera:
`docker stop smartfin-postgres && docker rm smartfin-postgres`
- Sprawdzenie logów kontenera:
`docker logs smartfin-postgres`
- Sprawdzenie stanu kontenera:
`docker ps -a | grep smartfin-postgres`
- Sprawdzenie dostępnych obrazów Dockera:
`docker images | grep postgres`
- Usunięcie obrazu Dockera (jeśli chcesz zwolnić miejsce):
`docker rmi postgres:16-alpine`


PS C:\dev\smart-fin-analyzer> docker run -d --name smartfin-postgres -e POSTGRES_DB=smartfin_db -e POSTGRES_USER=smartfin -e POSTGRES_PASSWORD=smartfin123 -p 5432:5432 postgres:16-alpine
5d6dc297fa96c38006e5436d5b7c> Start-Sleep -Seconds 3 ; docker exec -it smartfin-postgres psql -U smartfin -d smartfin_db -c "\conninfo"
You are connected to database "smartfin_db" as user "smartfin" via socket in "/var/run/postgresql" at port "5432".
PS C:\dev\smart-fin-analyzer> docker exec smartfin-postgres psql -U smartfin -d smartfin_db -c "SELECT version();"
version
------------------------------------------------------------------------------------------
PostgreSQL 16.13 on x86_64-pc-linux-musl, compiled by gcc (Alpine 15.2.0) 15.2.0, 64-bit
(1 row)

PS C:\dev\smart-fin-analyzer> cd C:\dev\smart-fin-analyzer ; .\gradlew.bat test --tests "pl.edu.praktyki.repository.IntegrationDbSpec" 2>&1 | Select-String -Pattern "(PASSED|FAILED|Hibernate:|Started)" | Out-String

    21:49:57.579 [Test worker] INFO  p.e.p.repository.IntegrationDbSpec - Starting IntegrationDbSpec using Java 17.0.15 with PID 32088 (started by j4sys in C:\dev\smart-fin-analy
zer)
Hibernate: alter table if exists transaction_entity_tags drop constraint if exists FK86dh9282k39dke0f13fam8pv0
Hibernate: drop table if exists counters cascade
Hibernate: drop table if exists transaction_entity_tags cascade
Hibernate: drop table if exists transactions cascade
Hibernate: create table counters (counter_value integer, id bigserial not null, name varchar(255) not null unique, primary key (id))
Hibernate: create table transaction_entity_tags (transaction_entity_db_id bigint not null, tags varchar(255))
Hibernate: create table transactions (amount numeric(38,2), amountpln numeric(38,2), date date, db_id bigserial not null, category varchar(255), currency varchar(255), descri
ption varchar(255), original_id varchar(255), primary key (db_id))
Hibernate: alter table if exists transaction_entity_tags add constraint FK86dh9282k39dke0f13fam8pv0 foreign key (transaction_entity_db_id) references transactions
21:50:20.721 [Test worker] INFO  p.e.p.repository.IntegrationDbSpec - Started IntegrationDbSpec in 23.741 seconds (process running for 28.214)
Hibernate: select count(*) from transactions te1_0
Hibernate: insert into transactions (amount,amountpln,category,currency,date,description,original_id) values (?,?,?,?,?,?,?)
Hibernate: select te1_0.db_id,te1_0.amount,te1_0.amountpln,te1_0.category,te1_0.currency,te1_0.date,te1_0.description,te1_0.original_id from transactions te1_0
Hibernate: select t1_0.transaction_entity_db_id,t1_0.tags from transaction_entity_tags t1_0 where t1_0.transaction_entity_db_id=?
Hibernate: select te1_0.db_id,te1_0.amount,te1_0.amountpln,te1_0.category,te1_0.currency,te1_0.date,te1_0.description,te1_0.original_id from transactions te1_0
Hibernate: select t1_0.transaction_entity_db_id,t1_0.tags from transaction_entity_tags t1_0 where t1_0.transaction_entity_db_id=?
IntegrationDbSpec > powinien zapisaŠ i odczytaŠ dane z prawdziwego PostgreSQL w kontenerze PASSED



PS C:\dev\smart-fin-analyzer> docker exec smartfin-postgres psql -U smartfin -d smartfin_db -c "\dt"
List of relations
Schema |          Name           | Type  |  Owner   
--------+-------------------------+-------+----------
public | counters                | table | smartfin
public | transaction_entity_tags | table | smartfin
public | transactions            | table | smartfin
(3 rows)

PS C:\dev\smart-fin-analyzer> docker exec smartfin-postgres psql -U smartfin -d smartfin_db -c "SELECT * FROM transactions;"
amount | amountpln | date | db_id | category | currency | description | original_id
--------+-----------+------+-------+----------+----------+-------------+-------------
500.00 |           |      |     1 | Test     |          |             | DB-1
(1 row)

PS C:\dev\smart-fin-analyzer> docker exec smartfin-postgres psql -U smartfin -d smartfin_db -c "\d transactions"
Table "public.transactions"
Column    |          Type          | Collation | Nullable |                   Default                   
-------------+------------------------+-----------+----------+---------------------------------------------
amount      | numeric(38,2)          |           |          |
amountpln   | numeric(38,2)          |           |          |
date        | date                   |           |          |
db_id       | bigint                 |           | not null | nextval('transactions_db_id_seq'::regclass)
category    | character varying(255) |           |          |
currency    | character varying(255) |           |          |
description | character varying(255) |           |          |
original_id | character varying(255) |           |          |
Indexes:
"transactions_pkey" PRIMARY KEY, btree (db_id)
Referenced by:
TABLE "transaction_entity_tags" CONSTRAINT "fk86dh9282k39dke0f13fam8pv0" FOREIGN KEY (transaction_entity_db_id) REFERENCES transactions(db_id)

PS C:\dev\smart-fin-analyzer>




Działa pięknie! Oto podsumowanie:
🐘 Twój PostgreSQL jest gotowy do inspekcji
Parametr          Wartość            
-------------------------------- 
Host              localhost:5432
Baza              smartfin_db
User              smartfin
Hasło             smartfin123

3 tabele utworzone przez Hibernate:
transactions — główna tabela z transakcjami (1 rekord z testu: DB-1, 500.00 zł)
transaction_entity_tags — tagi powiązane z transakcjami (relacja 1:N)
counters — liczniki
Przydatne komendy psql:


# Wejdź do interaktywnej konsoli psql
docker exec -it smartfin-postgres psql -U smartfin -d smartfin_db

# Pokaż tabele:       \dt
# Struktura tabeli:    \d transactions
# Wszystkie dane:      SELECT * FROM transactions;
# Wyjście z psql:      \q



Narzędzia GUI:
Możesz też podłączyć się z DBeaver, pgAdmin lub IntelliJ Database Tool — po prostu wpisz powyższe dane połączenia.
Uwaga: Gdy skończysz inspekcję, przywróć profil tc w teście (Testcontainers) i włącz cleanup(), żeby test był powtarzalny. Kontener możesz zatrzymać przez: docker stop smartfin-postgres && docker rm smartfin-postgres





pozostale test będą używały profilu `test` z H2, więc nie będą kolidować z Testcontainers.
przykladowe polecenie do uruchomienia testów z profilem `test`:
`./gradlew test --tests "pl.edu.praktyki.repository.*" -Dspring.profiles.active=test`





Jak działa ta "Magia" Testcontainers? (Dlaczego to jest genialne?)
Zwróć uwagę na ten wpis:
`jdbc:tc:postgresql:16-alpine:///smartfin_db`
Normalnie w Javie URL do bazy wygląda tak: `jdbc:postgresql://localhost:5432/baza`.
Gdy dodajemy słówko `tc` (TestContainers), dzieje się coś niesamowitego:
Spring próbuje połączyć się z bazą.
Zanim to nastąpi, specjalny sterownik `Testcontainers` "przechwytuje" to żądanie.
Testcontainers łączy się z Twoim `Dockerem` (działającym w tle na Twoim komputerze).
Ściąga obraz `postgres:16-alpine` z internetu i uruchamia go na losowym porcie (np. 32768, żeby nie blokować prawdziwej bazy, jeśli jakąś masz).
Podmienia URL w Springu tak, aby wskazywał na ten nowy, świeżutki kontener.
Po zakończeniu testu (Gdy Spock kończy pracę), Testcontainers "zabija" i usuwa kontener z Dockera, sprzątając po sobie.
Dzięki temu masz 100% pewności, że Twoje zapytania zadziałają na produkcji, bo testujesz je na identycznym silniku bazy danych, a nie na jego symulacji (H2).




3. Test Integracyjny z Dockerem (IntegrationDbSpec.groovy)

To jest moment prawdy. Jeśli ten test przejdzie, Twoja aplikacja jest gotowa na prawdziwą bazę danych.

Stwórz `src/test/groovy/pl/edu/praktyki/repository/IntegrationDbSpec.groovy`:

```groovy
package pl.edu.praktyki.repository

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.ContextConfiguration
import pl.edu.praktyki.SmartFinDbApp
import spock.lang.Specification

@SpringBootTest(classes = [SmartFinDbApp])
@ContextConfiguration // Wymagane przez Spock-Spring 2.3 do aktywacji SpringExtension
// TYMCZASOWO: "local-pg" zamiast "tc" — dane zostaną w Twoim kontenerze PostgreSQL
@ActiveProfiles(["test", "local-pg"])
class IntegrationDbSpec extends Specification {

    @Autowired
    TransactionRepository repository


    // cleanup wyłączony tymczasowo — dane zostają w bazie do inspekcji
    // def cleanup() {
    //     repository?.deleteAll()
    // }

    def "powinien zapisać i odczytać dane z prawdziwego PostgreSQL w kontenerze"() {
        given: "nowa encja"
        def entity = new TransactionEntity(
                originalId: "DB-1",
                amount: 500.0,
                category: "Test"
        )

        when: "zapisujemy w prawdziwej bazie"
        repository.save(entity)

        then: "dane są w bazie"
        repository.findAll().size() == 1
        repository.findAll()[0].originalId == "DB-1"
    }
}
```

Dlaczego to jest "Mid-level Skill"?

Paradygmat "Infrastructure as Code" w testach: 
Twój test sam dba o swoje środowisko. 
Nie musisz prosić admina o stworzenie bazy danych. 
Jeśli ktoś ściągnie Twój projekt na inny komputer, testy same odpalą sobie bazę w Dockerze.

Unikanie różnic: 
Zapominasz o błędach typu "U mnie na H2 działa, a na produkcji na Postgresie rzuca błąd składni".

Wiarygodność: 
Test integracyjny z prawdziwą bazą to 100x większa pewność działania niż test z H2.

 
Podsumowanie:
Testcontainers to potężne narzędzie, które pozwala Ci testować integrację z bazą danych w sposób bardzo zbliżony do produkcji, bez konieczności ręcznej konfiguracji. 
To kluczowy krok w kierunku kuloodpornych testów integracyjnych, które dają Ci pewność, że Twoja aplikacja będzie działać poprawnie w rzeczywistym środowisku. 
Dzięki temu możesz spać spokojnie, wiedząc, że Twoje testy nie są tylko symulacją, ale prawdziwym sprawdzianem dla Twojej aplikacji.

 