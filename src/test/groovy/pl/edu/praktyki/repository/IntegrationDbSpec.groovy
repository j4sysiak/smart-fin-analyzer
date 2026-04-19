package pl.edu.praktyki.repository

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import pl.edu.praktyki.BaseIntegrationSpec

/**
 * Test integracyjny — zapis/odczyt z prawdziwego PostgreSQL w kontenerze Docker.
 *
 * Profil 'tc' jest dziedziczony z BaseIntegrationSpec — PostgreSQL
 * startuje automatycznie w kontenerze Docker (Docker CLI, port 15432).
 *
 * KNOW HOW — ręczna inspekcja bazy (tryb local-pg):
 *   Wystarczy dodać flagę -Dlocal.pg=true — bez zmian w kodzie!
 *   Patrz: application-local-pg.properties
 */
@AutoConfigureMockMvc
@org.springframework.transaction.annotation.Transactional
@org.springframework.test.annotation.Rollback
class IntegrationDbSpec extends BaseIntegrationSpec {

/*
Reczna inspekcja z prawdziwym PostgreSQL-em w kontenerze Docker:

1. Uruchom kontener PostgreSQL:
   docker run -d --name smartfin-postgres \
     -e POSTGRES_DB=smartfin_test -e POSTGRES_USER=finuser -e POSTGRES_PASSWORD=finpass \
     -p 5432:5432 postgres:16-alpine

2. Uruchom test z flaga local-pg (BEZ zmian w kodzie!):
   ./gradlew test --tests "pl.edu.praktyki.repository.IntegrationDbSpec" "-Dlocal.pg=true"

3. Polacz sie z baza:
   docker exec -it smartfin-postgres psql -U finuser -d smartfin_test

   Przydatne komendy psql:
     \dt                        — pokaz tabele
     \d transactions            — struktura tabeli
     SELECT * FROM transactions — wszystkie dane
     \q                         — wyjscie

4. Zatrzymaj kontener: docker stop smartfin-postgres && docker rm smartfin-postgres
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