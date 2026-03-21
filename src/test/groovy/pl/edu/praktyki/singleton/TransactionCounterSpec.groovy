package pl.edu.praktyki.singleton

import groovyx.gpars.GParsPool
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.test.context.ActiveProfiles
import pl.edu.praktyki.BaseIntegrationSpec
import pl.edu.praktyki.repository.Counter
import pl.edu.praktyki.repository.CounterRepository

@AutoConfigureMockMvc
@ActiveProfiles(value = ["tc"], inheritProfiles = false)

// Wymusi użycie application-local-pg.properties ale musisz mieć wlączony lokalny Postgresa!
// (nie działa z H2, bo H2 nie obsługuje funkcji SQL, których używamy w repozytorium)
// @ActiveProfiles(value = ["local-pg"], inheritProfiles = false)
class TransactionCounterSpec extends BaseIntegrationSpec { // <-- DZIEDZICZYMY!

    @Autowired
    TransactionCounterService counterService

    @Autowired
    CounterRepository counterRepository

    def setup() {
        // przygotuj rekord w bazie przed testem
        counterRepository.deleteAll()
        counterRepository.save(new Counter(name: "requests", value: 0))
    }

    def "baza danych powinna bezpiecznie inkrementować licznik wielowątkowo"() {
        when: "10000 wątków inkrementuje ten sam licznik w bazie"
        GParsPool.withPool {
            (1..10000).collectParallel {
                counterService.increment("requests")
            }
        }

        then: "wynik wynosi dokładnie 10000"
        counterService.getCounter("requests") == 10000
    }
}