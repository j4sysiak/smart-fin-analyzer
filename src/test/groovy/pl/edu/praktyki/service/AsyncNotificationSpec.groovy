package pl.edu.praktyki.service

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationEventPublisher
import pl.edu.praktyki.BaseIntegrationSpec
import pl.edu.praktyki.event.TransactionBatchProcessedEvent

import java.util.concurrent.TimeUnit

import static org.awaitility.Awaitility.await

class AsyncNotificationSpec extends BaseIntegrationSpec {

    @Autowired ApplicationEventPublisher eventPublisher
    @Autowired AsyncNotificationService notificationService
    @Autowired ThreadTracker threadTracker

    def "powinien przetworzyć powiadomienie asynchronicznie w osobnym wątku"() {
        given: "czysty stan licznika"
        notificationService.reset()
        def event = new TransactionBatchProcessedEvent(
                userName: "AsyncTester",
                totalBalance: 1500.0,
                transactionsCount: 10L,
                generatedReport: "Test Report Content"
        )

        when: "publikujemy zdarzenie"
        long startTime = System.currentTimeMillis()
        eventPublisher.publishEvent(event)
        long publishTime = System.currentTimeMillis() - startTime

        then: "1. Publikacja musi być natychmiastowa (nie blokuje jej sleep 6s)"
        // Gdyby metoda była synchroniczna, ten test trwałby min. 6 sekund
        publishTime < 1000

        and: "2. W tym momencie licznik wciąż powinien być na 0"
        notificationService.getProcessedCount() == 0

        then: "3. Czekamy asynchronicznie (max 15s), aż praca w tle się zakończy"
        await().atMost(15, TimeUnit.SECONDS).until {
            notificationService.getProcessedCount() == 1
        }

        and: "4. Weryfikujemy dane zapisane w ThreadTrackerze"
        Map stats = threadTracker.get('AsyncNotificationService.handleBatchEvent') as Map

        stats != null
        stats.user == "AsyncTester"
        stats.count == 10L

        // Sprawdzamy, czy wątek ma poprawny prefiks z Twojego `AsyncConfig`
        // (W logach pisałeś, że masz: bulkTaskExecutorZapierdala--)
        stats.thread.startsWith("bulkTaskExecutorZapierdala--")

        and: "wyświetlamy diagnostykę"
        println ">>> Test zakończony sukcesem."
        println ">>> Zadanie wykonane przez wątek: ${stats.thread}"
        println ">>> Czas trwania publikacji (synchronicznie): ${publishTime}ms"
    }
}