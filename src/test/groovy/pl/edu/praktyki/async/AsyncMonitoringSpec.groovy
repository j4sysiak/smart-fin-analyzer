package pl.edu.praktyki.async

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationEventPublisher
import pl.edu.praktyki.BaseIntegrationSpec
import pl.edu.praktyki.event.TransactionBatchProcessedEvent
import pl.edu.praktyki.service.ThreadTracker

import static org.awaitility.Awaitility.await
import java.util.concurrent.TimeUnit

class AsyncMonitoringSpec extends BaseIntegrationSpec {

    @Autowired ApplicationEventPublisher eventPublisher
    @Autowired ThreadTracker threadTracker

    def "powinien zarejestrować informację o wątku z poprawnej puli w ThreadTrackerze"() {
        given: "zdarzenie przetwarzania paczki"
        def event = new TransactionBatchProcessedEvent(
                userName: "MonitorTester",
                totalBalance: 100.0,
                transactionsCount: 5
        )

        when: "publikujemy zdarzenie, które uruchamia asynchronicznego słuchacza"
        eventPublisher.publishEvent(event)

        then: "Czekamy asynchronicznie, aż tracker zostanie uzupełniony"
        await().atMost(5, TimeUnit.SECONDS).until {
            // POPRAWKA KLUCZA: musi być identyczny jak w GlobalStatsProjector
            threadTracker.get('GlobalStatsProjector.lastThread') != null
        }

        and: "wyciągamy dane z trackera"
        Map stats = threadTracker.get('GlobalStatsProjector.lastThread') as Map

        then: "wątek musi mieć Twój nowy prefiks z logów"
        // Zmień to na to, co faktycznie masz w AsyncConfig!
        stats.thread.startsWith("bulkTaskExecutorZapierdala--")
        stats.user == "MonitorTester"

        println ">>> SUKCES! Znaleziono dane dla wątku: ${stats.thread}"

        and: "możemy wypisać statystyki dla pewności"
        println ">>> Przetworzono na wątku: ${stats.thread} o czasie: ${stats.ts}"
    }
}