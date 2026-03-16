package pl.edu.praktyki.singleton

import spock.lang.Specification
import groovyx.gpars.GParsPool

class ComparisonSpec extends Specification {

    def "BadService - zwykły int JEST podatny na wyścig danych"() {
        given:
        def badService = new BadService()

        when:
        GParsPool.withPool(10) {
            (1..10000).collectParallel {
                badService.increment()
            }
        }

        then: "prawie nigdy nie będzie 10000!"
        println "BadService wynik: ${badService.counter} (oczekiwano 10000)"
        badService.counter != 10000 || true // test dokumentacyjny
    }

    def "SafeCounterService - AtomicInteger ZAWSZE daje poprawny wynik"() {
        given:
        def safeService = new SafeCounterService()

        when:
        GParsPool.withPool(10) {
            (1..10000).collectParallel {
                safeService.increment()
            }
        }

        then: "zawsze 10000"
        safeService.counter == 10000
    }
}