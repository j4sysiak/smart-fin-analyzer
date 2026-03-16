package pl.edu.praktyki.singleton

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.ContextConfiguration
import spock.lang.Specification
import groovyx.gpars.GParsPool
import pl.edu.praktyki.SmartFinDbApp

@SpringBootTest(classes = [SmartFinDbApp])
@ActiveProfiles("test")
@ContextConfiguration
class TransactionCounterSpec extends Specification {

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
        when: "100 wątków inkrementuje ten sam licznik w bazie"
        GParsPool.withPool {
            (1..100).collectParallel {
                counterService.increment("requests")
            }
        }

        then: "wynik wynosi dokładnie 100"
        counterService.getCounter("requests") == 100
    }
}