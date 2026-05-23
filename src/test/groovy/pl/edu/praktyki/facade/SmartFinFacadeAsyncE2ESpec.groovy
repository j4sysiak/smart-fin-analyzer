package pl.edu.praktyki.facade

import org.springframework.beans.factory.annotation.Autowired
import pl.edu.praktyki.BaseIntegrationSpec
import pl.edu.praktyki.domain.TransactionDto
import pl.edu.praktyki.repository.FinancialSummaryRepository
import pl.edu.praktyki.repository.TransactionRepository
import pl.edu.praktyki.service.AsyncNotificationService
import pl.edu.praktyki.service.ThreadTracker

import java.time.LocalDate
import java.util.concurrent.TimeUnit

import static org.awaitility.Awaitility.await

class SmartFinFacadeAsyncE2ESpec extends BaseIntegrationSpec {

    @Autowired
    SmartFinFacade facade

    @Autowired
    TransactionRepository transactionRepository

    @Autowired
    FinancialSummaryRepository summaryRepository

    @Autowired
    AsyncNotificationService notificationService

    @Autowired
    ThreadTracker threadTracker

    def "powinien asynchronicznie przetworzyć paczkę przez fasadę i dowieźć efekty biznesowe end-to-end"() {
        given: "wejście biznesowe i czysty stan hookow"
        String user = "FacadeE2EUser"
        String originalId = "E2E-ASYNC-1"

        notificationService.reset()
        threadTracker.remove('SmartFinFacade.processInBackgroundTask')
        threadTracker.remove('AuditEventListener.onBatchProcessed')
        threadTracker.remove('GlobalStatsProjector.lastThread')
        threadTracker.remove("AsyncNotificationService.completed.${user}")

        double startGlobal = summaryRepository.findById("GLOBAL").map { it.totalBalance }.orElse(0.0)

        def transactions = [
                new TransactionDto(
                        id: originalId,
                        amount: 250.0,
                        currency: "PLN",
                        category: "E2E",
                        description: "async e2e",
                        date: LocalDate.now()
                )
        ]

        when: "klient odpala async endpoint fasady"
        long start = System.currentTimeMillis()
        facade.processInBackgroundTask(user, transactions, [])
        long durationMs = System.currentTimeMillis() - start

        then: "API wraca szybko i nie czeka na ciężkie przetwarzanie"
        durationMs < 1000

        when: "czekamy az cały łańcuch biznesowy dobiegnie końca"
        await().atMost(12, TimeUnit.SECONDS).until {
            def savedOpt = transactionRepository.findByOriginalIdAndOwnerUsername(originalId, user)
            Map audit = threadTracker.get('AuditEventListener.onBatchProcessed') as Map
            Map projector = threadTracker.get('GlobalStatsProjector.lastThread') as Map
            Map facadeAsync = threadTracker.get('SmartFinFacade.processInBackgroundTask') as Map
            Map notifyDone = threadTracker.get("AsyncNotificationService.completed.${user}") as Map
            double globalNow = summaryRepository.findById("GLOBAL").map { it.totalBalance }.orElse(0.0)

            savedOpt.present &&
                    notificationService.getProcessedCount() == 1 &&
                    audit?.user == user &&
                    projector?.user == user &&
                    facadeAsync?.user == user &&
                    notifyDone?.user == user &&
                    globalNow == (startGlobal + 250.0d)
        }

        then: "rekord transakcji jest zapisany z poprawnym ownerem"
        def saved = transactionRepository.findByOriginalIdAndOwnerUsername(originalId, user)
                .orElseThrow()
        saved.ownerUsername == user
        saved.originalId == originalId
        saved.currency == "PLN"

        and: "projekcja GLOBAL odzwierciedla przetworzony bilans"
        summaryRepository.findById("GLOBAL").orElseThrow().totalBalance == (startGlobal + 250.0d)

        and: "metadane async pokazuja przejscie przez pule bulk"
        Map facadeAsyncStats = threadTracker.get('SmartFinFacade.processInBackgroundTask') as Map
        facadeAsyncStats.thread?.startsWith("bulkTaskExecutorZapierdala--")
    }
}
