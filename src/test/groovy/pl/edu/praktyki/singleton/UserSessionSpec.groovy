package pl.edu.praktyki.singleton

import spock.lang.Specification
import groovyx.gpars.GParsPool

class UserSessionSpec extends Specification {

    def "ConcurrentHashMap powinien zliczać próby logowania bezpiecznie"() {
        given:
        def service = new UserSessionService()

        when: "50000 wątków rejestruje próby dla tego samego użytkownika"
        GParsPool.withPool {
            (1..50000).collectParallel {
                service.recordAttempt("jan_kowalski")
            }
        }

        then: "liczba prób wynosi dokładnie 50000"
        service.getAttempts("jan_kowalski") == 50000
    }

    def "powinien zliczać niezależnie dla różnych użytkowników"() {
        given:
        def service = new UserSessionService()

        when:
        GParsPool.withPool {
            (1..3000).collectParallel {
                service.recordAttempt("user_A")
            }
            (1..2000).collectParallel {
                service.recordAttempt("user_B")
            }
        }

        then:
        service.getAttempts("user_A") == 3000
        service.getAttempts("user_B") == 2000
    }
}