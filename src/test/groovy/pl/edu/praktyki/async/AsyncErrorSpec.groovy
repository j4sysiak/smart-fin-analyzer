package pl.edu.praktyki.async

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import org.springframework.scheduling.annotation.Async
import pl.edu.praktyki.BaseIntegrationSpec
import pl.edu.praktyki.service.EvilService
import pl.edu.praktyki.service.ThreadTracker
import static org.awaitility.Awaitility.await
import java.util.concurrent.TimeUnit


class AsyncErrorSpec extends BaseIntegrationSpec {

    @Autowired EvilService evilService
    @Autowired ThreadTracker threadTracker

    def "powinien przechwycić i zarejestrować wyjątek rzucony asynchronicznie"() {
        when: "odpalamy zadanie, które na 100% wybuchnie"
        evilService.throwErrorAsync()

        then: "Czekamy asynchronicznie, aż Globalny Handler złapie błąd i wpisze do trackera"
        await().atMost(5, TimeUnit.SECONDS).until {
            threadTracker.get("ERROR.throwErrorAsync") != null
        }

        and: "dane błędu w trackerze są poprawne"
        Map errorInfo = threadTracker.get("ERROR.throwErrorAsync") as Map
        errorInfo.message == "KATASTROFA W TLE"
        errorInfo.exception == "RuntimeException"

        println ">>> SUKCES: Wyjątek w tle został poprawnie przechwycony przez CustomAsyncExceptionHandler!"
    }
}