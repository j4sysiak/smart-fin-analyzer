package pl.edu.praktyki.repository

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.test.context.ActiveProfiles
import pl.edu.praktyki.BaseIntegrationSpec


// 1. @ActiveProfiles(value = ["local-pg"], inheritProfiles = false) powoduje,
//    że kontekst testowy ładuje profil local-pg i w efekcie użyje ustawień z pliku application-local-pg.properties
//    (albo application-local-pg.yml) zamiast domyślnych.
// 2. Test uruchamia pełny kontekst Springa (MockMvc + repozytoria) i będzie próbował połączyć się
//    z bazą zgodnie z ustawieniami z tego pliku.
//    Komentarz w kodzie słusznie mówi: musisz mieć lokalnego Postgresa uruchomionego — test tego serwera
//    nie uruchomi samodzielnie.
// 3. inheritProfiles = false oznacza, że tylko local-pg jest aktywny (inne profile domyślne nie są łączone).
// 4. H2 nie będzie działać w tym projekcie (jak napisano), więc albo uruchom lokalny Postgres,
//    albo użyj Testcontainers / wbudowanego Postgresa, jeśli chcesz automatycznie startować DB w testach.
// 5. Sprawdź w application-local-pg.properties URL, username i password oraz czy schemat/bazy zostały przygotowane przed uruchomieniem testów.


@AutoConfigureMockMvc

//KNOW HOW!  (ActiveProfiles = 'tc')
// To nie dziala, żeby użyć Testcontainers - profil 'tc', to BaseIntegrationSpec ustaw warunek na if (1==1)
// wtedy zawsze będzie używał Testcontainers.
// Wtedy ten test będzie działał bez konieczności uruchamiania ręcznie Postgresa na Docker.
// wtedy postgres będzie uruchamiany automatycznie w kontenerze Docker przez Testcontainers,
// a po zakończeniu testów będzie automatycznie zatrzymywany i usuwany.

//@ActiveProfiles(value = ["tc"], inheritProfiles = false)


//KNOW HOW!  (ActiveProfiles = 'local-pg')
// Wymusi użycie application-local-pg.properties ale musisz mieć wlączony lokalny Postgresa!
// (nie działa z H2, bo H2 nie obsługuje funkcji SQL, których używamy w repozytorium)
// tutaj info jak uruchomić lokalnego postgresa na dokerze dla profilu: local-pg:
//                     C:\dev\smart-fin-analyzer\src\test\resources\application-local-pg.properties

@ActiveProfiles(value = ["local-pg"], inheritProfiles = false)
class IntegrationDbSpec extends BaseIntegrationSpec {

/*
test inspekcja z prawdziwym PostgreSQL-em w kontenerze Docker — bez Testcontainers, ręcznie:
1. Ustaw profil "local-pg" w @ActiveProfiles

2. Uruchom najpierw kontener PostgreSQL:
docker run -d --name smartfin-postgres -e POSTGRES_DB=smartfin_db -e POSTGRES_USER=smartfin -e POSTGRES_PASSWORD=smartfin123 -p 5432:5432 postgres:16-alpine

3. Pamiętaj, żeby mieć wyłączony cleanup()!

4. Uruchom test:
./gradlew test --tests "pl.edu.praktyki.repository.IntegrationDbSpec" -Dtest.single=IntegrationDbSpec -Dspring.profiles.active=test,local-pg



Działa pięknie! Oto podsumowanie:
Twój PostgreSQL jest gotowy do inspekcji

Parametr          Wartość
--------------------------------
Host              localhost:5432
Baza              smartfin_db
User              smartfin
Hasło             smartfin123

    3 tabele utworzone przez Hibernate:
    transactions            — główna tabela z transakcjami (1 rekord z testu: DB-1, 500.00 zł)
    transaction_entity_tags — tagi powiązane z transakcjami (relacja 1:N)
    counters                — liczniki

    Przydatne komendy psql:
    -----------------------

    # Wejdź do interaktywnej konsoli psql
    docker exec -it smartfin-postgres psql -U smartfin -d smartfin_db

    # Pokaż tabele:       \dt
    # Struktura tabeli:    \d transactions
    # Wszystkie dane:      SELECT * FROM transactions;
    # Wyjście z psql:      \q


    Narzędzia GUI:
    Możesz też podłączyć się z DBeaver, pgAdmin lub IntelliJ Database Tool — po prostu wpisz powyższe dane połączenia.
    Uwaga: Gdy skończysz inspekcję, przywróć profil tc w teście (Testcontainers) i włącz cleanup(), żeby test był powtarzalny. Kontener możesz zatrzymać przez: docker stop smartfin-postgres && docker rm smartfin-postgres

*/

    @Autowired
    TransactionRepository repository

    def setup() {
        // Przed każdym testem czyścimy bazę i dodajemy świeże dane
        repository.deleteAll()
    }

    def "powinien zapisać TransactionEntity i odczytać dane z prawdziwego PostgreSQL w kontenerze"() {
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