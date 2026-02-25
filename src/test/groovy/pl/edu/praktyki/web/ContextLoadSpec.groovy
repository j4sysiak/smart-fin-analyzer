package pl.edu.praktyki.web

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationContext
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ContextConfiguration
import spock.lang.Specification
import pl.edu.praktyki.SmartFinDbApp

/**
 * Smoke test - weryfikuje, czy kontekst Spring Boot ładuje się poprawnie.
 *
 * UWAGA: Spock-Spring 2.3 nie wykrywa samego @SpringBootTest jako triggera
 * integracji Spring. Dodajemy @ContextConfiguration, aby Spock aktywował SpringExtension.
 */
@SpringBootTest(classes = [SmartFinDbApp])
@ContextConfiguration
class ContextLoadSpec extends Specification {

    @Autowired
    ApplicationContext ctx

    def "kontekst Spring Boot powinien sie zaladowac"() {
        expect:
        ctx != null
    }
}

