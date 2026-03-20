package pl.edu.praktyki.repository

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.test.context.ActiveProfiles
import pl.edu.praktyki.BaseIntegrationSpec
import spock.lang.Specification

// Dziedziczymy konfigurację testową (SpringBootTest, ContextConfiguration, Testcontainers)
// z BaseIntegrationSpec
// TYMCZASOWO: "local-pg" zamiast "tc" — dane zostaną w Twoim kontenerze PostgreSQL
// @ActiveProfiles(["test", "local-pg"]) //  <-- Ustaw ten profil, gdy chcesz połączyć się z lokalnym PostgreSQL (pamiętaj o cleanup()!)

@AutoConfigureMockMvc
@ActiveProfiles(value = ["tc"], inheritProfiles = false)       //  <-- Ustawiamy tylko 'tc' żeby uniknać konfliktu z H2 (profil 'test')
class IntegrationDbSpec extends BaseIntegrationSpec { // <-- DZIEDZICZYMY!

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