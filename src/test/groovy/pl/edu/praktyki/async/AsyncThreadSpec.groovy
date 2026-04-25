package pl.edu.praktyki.async

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationEventPublisher
import pl.edu.praktyki.BaseIntegrationSpec
import pl.edu.praktyki.event.TransactionBatchProcessedEvent
import spock.lang.Shared
import java.util.concurrent.ConcurrentHashMap
import static org.awaitility.Awaitility.await
import java.util.concurrent.TimeUnit

class AsyncThreadSpec extends BaseIntegrationSpec {

    @Autowired ApplicationEventPublisher eventPublisher

    // Mapa do zapisania nazwy wątku, który obsłużył event
    @Shared def threadMap = new ConcurrentHashMap<String, String>()

    def "powinien wykonać zadanie na dedykowanej puli wątków bulkTaskExecutor"() {
        given: "zdarzenie"
        def event = new TransactionBatchProcessedEvent(userName: "ThreadTester", totalBalance: 10)

        when: "publikujemy event"
        eventPublisher.publishEvent(event)

        then: "czekamy, aż system go obsłuży i sprawdzamy nazwę wątku"
        // Musisz w swoim Listenerze (np. GlobalStatsProjector) dopisać:
        // threadMap["lastThread"] = Thread.currentThread().name

        await().atMost(5, TimeUnit.SECONDS).until {
            // W logach powinieneś widzieć: "bulkTaskExecutorZapierdala--"
            println ">>> Zadanie wykonane przez wątek: ${Thread.currentThread().name}"
            return true
        }
    }
}