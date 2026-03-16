package pl.edu.praktyki.singleton

import spock.lang.Specification
import groovyx.gpars.GParsPool

class SafeCounterSpec extends Specification {

    def "AtomicInteger powinien być bezpieczny wielowątkowo"() {
        given:
        def service = new SafeCounterService()

        when: "100 wątków inkrementuje licznik"
        GParsPool.withPool {
            (1..100).collectParallel {
                service.increment()
            }
        }

        then: "wynik zawsze wynosi 100"
        service.counter == 100
    }
}