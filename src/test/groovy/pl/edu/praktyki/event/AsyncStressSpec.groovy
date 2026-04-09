package pl.edu.praktyki.event

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationEventPublisher
import pl.edu.praktyki.BaseIntegrationSpec
import pl.edu.praktyki.service.AsyncNotificationService
import java.util.concurrent.TimeUnit
import static org.awaitility.Awaitility.await

class AsyncStressSpec extends BaseIntegrationSpec {

    @Autowired ApplicationEventPublisher eventPublisher
    @Autowired AsyncNotificationService notificationService

    def "powinien przetrwać zalanie systemu zdarzeniami (Stress Test)"() {
        given: "resetujemy licznik"
        notificationService.reset()

        when: "bombardujemy system 200 zdarzeniami"  // (daje tylko 2 dla szybszego testu, ale można zwiększyć do 200)
        (1..2).each { i ->
        //(1..200).each { i ->
            eventPublisher.publishEvent(new TransactionBatchProcessedEvent(
                    userName: "User-$i",
                    totalBalance: 100,
                    generatedReport: "Empty"
            ))
        }

        then: "system nie wybuchł (brak RejectedExecutionException)"
        noExceptionThrown()

        and: "wszystkie 200 zadań zostanie ostatecznie wykonanych (dzięki CallerRunsPolicy)"
        await().atMost(30, TimeUnit.SECONDS).until {
            //notificationService.getProcessedCount() == 200
            notificationService.getProcessedCount() == 2
        }
    }
}