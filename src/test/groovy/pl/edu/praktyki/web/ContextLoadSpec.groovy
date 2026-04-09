package pl.edu.praktyki.web

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationContext
import pl.edu.praktyki.BaseIntegrationSpec

/**
 * Smoke test - weryfikuje, czy kontekst Spring Boot ładuje się poprawnie.
 * Dziedziczy po BaseIntegrationSpec — baza danych pochodzi z Testcontainers.
 */
class ContextLoadSpec extends BaseIntegrationSpec {

    @Autowired
    ApplicationContext ctx

    def "kontekst Spring Boot powinien sie zaladowac"() {
        expect:
        ctx != null
    }
}

