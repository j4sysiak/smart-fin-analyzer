Lab 61
------

Lab61--Testcontainers-Baza-danych-w-kontenerze--Testy-Integracyjne-na-Postgresie-różne-profile-testowania
---------------------------------------------------------------------------------------------------------

Zaczynamy realizację Etapu 1: Kuloodporne Testy i Izolacja.

Mamy już za sobą WireMock (Lab61), 
   więc teraz przechodzimy do "ciężkiej artylerii" testowania integracyjnego: `Testcontainers`.

Problem: 
Testowanie na bazie H2 (in-memory) to nie to samo, co baza PostgreSQL na produkcji. 
H2 ma inne dialekty SQL, inną obsługę typów danych i inną wydajność. 
Możesz napisać test, który przechodzi na H2, a po wdrożeniu na produkcję (PostgreSQL) wywali się z błędem składni SQL.

Rozwiązanie:
`Testcontainers` uruchamia prawdziwego `PostgreSQL` w kontenerze Dockerowym, 
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


2. Test Integracyjny z Dockerem (IntegrationDbSpec.groovy)

To jest moment prawdy. 
Jeśli ten test przejdzie, Twoja aplikacja jest gotowa na prawdziwą bazę danych.

Stwórz `src/test/groovy/pl/edu/praktyki/repository/IntegrationDbSpec.groovy`:
Mamy 2 profile do wyboru: `tc` (Testcontainers) i `local-pg` (lokalny PostgreSQL w Dockerze).
i pamiętaj, żeby w teście ustawić profil `tc` i tymczasowo wyłączyć cleanup(), żeby dane zostały w bazie do inspekcji, 
ale po inspekcji przywrócić cleanup() do testu, żeby test był powtarzalny.

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


3. Konfiguracja application.properties

Tworzymy 3 profile: 
1. `tc` (dla `Testcontainers`) 
    Główna konfiguracja do testów integracyjnych - dane w bazie są tworzone i usuwane automatycznie.
    Idealna do testów CI/CD.
    Uruchomienie: `./gradlew test --tests "pl.edu.praktyki.repository.IntegrationDbSpec" -Dspring.profiles.active=tc`
    UWAGA: w teście `pl.edu.praktyki.repository.IntegrationDbSpec` ustawiamy profil `tc` 
           i tymczasowo wyłączamy cleanup() - komentujemy, żeby dane zostały w bazie do inspekcji, 
           ale pamiętaj, żeby po inspekcji przywrócić cleanup() do testu, żeby test był powtarzalny.

    ten profil używa `Testcontainers`, który automatycznie:
    - Odpala kontener postgres:16-alpine w Dockerze
    - Łączy się z nim na losowym porcie (w tym przypadku 50804)
    - Po teście automatycznie go zamyka i sprząta

    


2. `local-pg` (dla lokalnego Postgresa - tylko sprawdzenie co się dzieje w Dockerze, inspekcja danych w PostgreSQL).
    Dane zostają w bazie, żebyś mógł je podejrzeć po teście, ale musisz ręcznie zarządzać kontenerem PostgreSQL 
    (uruchamiać, zatrzymywać, czyścić),
    idealny do ręcznego testowania i inspekcji danych w kontenerze PostgreSQL, jeśli ktoś chce.
    Kroki do uruchomienia:
    
    - Ustaw profil "local-pg" w @ActiveProfiles

    - Uruchom najpierw kontener PostgreSQL:
    `docker run -d --name smartfin-postgres -e POSTGRES_DB=smartfin_db -e POSTGRES_USER=smartfin -e POSTGRES_PASSWORD=smartfin123 -p 5432:5432 postgres:16-alpine`

    - Pamiętaj, żeby mieć wyłączony cleanup()!

    - Uruchom test:
    `./gradlew test --tests "pl.edu.praktyki.repository.IntegrationDbSpec" -Dtest.single=IntegrationDbSpec -Dspring.profiles.active=test,local-pg`


UWAGA: ten profil jest tylko do ręcznego testowania i inspekcji danych w kontenerze PostgreSQL, 
           więc nie ma automatycznego cleanup() - dane zostają w bazie, żebyś mógł je podejrzeć po teście, 
           ale pamiętaj, że musisz ręcznie zarządzać kontenerem PostgreSQL (uruchamiać, zatrzymywać, czyścić), 
           więc używaj go tylko wtedy, gdy chcesz zobaczyć co się dzieje w Dockerze.
           Ten profil zakłada, że masz lokalnie uruchomiony kontener PostgreSQL, 
           i łączy się z nim na standardowym porcie 5432, 
           więc musisz mieć uruchomiony kontener PostgreSQL przed uruchomieniem testów z tym profilem, 
           a po zakończeniu testów musisz ręcznie zatrzymać i usunąć ten kontener, żeby nie zajmował zasobów.
 

 
 
polecenia do zarządzania lokalnym kontenerem PostgreSQL:
- Uruchomienie:
`docker run -d --name smartfin-postgres -e POSTGRES_DB=smartfin`
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



$ docker exec smartfin-postgres psql -U smartfin -d smartfin_db -c "\dt"
List of relations
Schema |          Name           | Type  |  Owner   
--------+-------------------------+-------+----------
public | counters                | table | smartfin
public | transaction_entity_tags | table | smartfin
public | transactions            | table | smartfin
(3 rows)

$ docker exec smartfin-postgres psql -U smartfin -d smartfin_db -c "SELECT * FROM transactions;"
amount | amountpln | date | db_id | category | currency | description | original_id
--------+-----------+------+-------+----------+----------+-------------+-------------
500.00 |           |      |     1 | Test     |          |             | DB-1
(1 row)

$ docker exec smartfin-postgres psql -U smartfin -d smartfin_db -c "\d transactions"
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
--------------
Możesz też podłączyć się z DBeaver, pgAdmin lub IntelliJ Database Tool — po prostu wpisz powyższe dane połączenia.
Uwaga: Gdy skończysz inspekcję, przywróć profil tc w teście (Testcontainers) i włącz cleanup(), żeby test był powtarzalny. Kontener możesz zatrzymać przez: docker stop smartfin-postgres && docker rm smartfin-postgres




3. Testy z profilem `test` (H2) — do pozostałych testów, które nie wymagają prawdziwej bazy danych,
pozostale test będą używały profilu `test` z H2, więc nie będą kolidować z Testcontainers.
przykladowe polecenie do uruchomienia testów z profilem `test`:
`./gradlew test --tests "pl.edu.praktyki.repository.*" -Dspring.profiles.active=test`



/////////////////////////  podsumowanie  /////////////////////////

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

 