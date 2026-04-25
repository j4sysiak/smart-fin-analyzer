package pl.edu.praktyki.service

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationEventPublisher
import pl.edu.praktyki.BaseIntegrationSpec
import pl.edu.praktyki.event.TransactionBatchProcessedEvent
import java.util.concurrent.TimeUnit

import static org.awaitility.Awaitility.await

class AuditEventListenerSpec extends BaseIntegrationSpec {

    @Autowired ApplicationEventPublisher eventPublisher
    @Autowired ThreadTracker threadTracker

    def "powinien asynchronicznie zarejestrować audyt po otrzymaniu zdarzenia o przetworzonej paczce"() {
        given: "zdarzenie o zakończeniu przetwarzania paczki"
        def event = new TransactionBatchProcessedEvent(
                userName: "AuditUser",
                totalBalance: 2500.0,
                transactionsCount: 15L,
                generatedReport: "Pełny raport audytowy..."
        )

        when: "publikujemy zdarzenie w systemie"
        // Nie wołamy listenera bezpośrednio! Udajemy, że Fasada skończyła pracę.
        eventPublisher.publishEvent(event)

        then: "Czekamy asynchronicznie, aż AuditEventListener zapisze dane w ThreadTrackerze"
        await().atMost(5, TimeUnit.SECONDS).until {
            threadTracker.get('AuditEventListener.onBatchProcessed') != null
        }

        and: "dane zapisane w trackerze są poprawne"
        Map stats = threadTracker.get('AuditEventListener.onBatchProcessed') as Map

        stats.user == "AuditUser"
        stats.count == 15L

        and: "operacja została wykonana na wątku z dedykowanej puli"
        // Weryfikujemy czy @Async("bulkTaskExecutor") zadziałało
        stats.thread.startsWith("bulkTaskExecutorZapierdala--")

        and: "wyświetlamy potwierdzenie w konsoli"
        println ">>> [AUDIT-TEST] Zdarzenie przechwycone przez wątek: ${stats.thread}"
        println ">>> [AUDIT-TEST] Dane użytkownika w audycie: ${stats.user}"
    }
}