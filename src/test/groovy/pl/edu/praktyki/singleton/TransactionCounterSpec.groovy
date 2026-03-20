package pl.edu.praktyki.singleton

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.ContextConfiguration
import pl.edu.praktyki.BaseIntegrationSpec
import pl.edu.praktyki.repository.Counter
import pl.edu.praktyki.repository.CounterRepository
import spock.lang.Specification
import groovyx.gpars.GParsPool
import pl.edu.praktyki.SmartFinDbApp

// @SpringBootTest(classes = [SmartFinDbApp])
// @ActiveProfiles("test")
// @ContextConfiguration

@AutoConfigureMockMvc
@ActiveProfiles(value = "tc", inheritProfiles = false)
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