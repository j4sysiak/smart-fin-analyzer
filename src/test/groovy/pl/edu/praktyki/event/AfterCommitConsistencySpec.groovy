package pl.edu.praktyki.event

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationEventPublisher
import org.springframework.transaction.support.TransactionTemplate
import pl.edu.praktyki.BaseIntegrationSpec
import pl.edu.praktyki.repository.FinancialSummaryRepository
import pl.edu.praktyki.service.AsyncNotificationService
import pl.edu.praktyki.service.ThreadTracker

import java.util.concurrent.TimeUnit

import static org.awaitility.Awaitility.await

class AfterCommitConsistencySpec extends BaseIntegrationSpec {

    @Autowired
    ApplicationEventPublisher eventPublisher

    @Autowired
    TransactionTemplate transactionTemplate

    @Autowired
    AsyncNotificationService notificationService

    @Autowired
    ThreadTracker threadTracker

    @Autowired
    FinancialSummaryRepository summaryRepo

    def "powinien uruchamiać listenery po COMMIT i nie uruchamiać ich po ROLLBACK"() {
        given: "czysty stan hookow i liczników"
        String commitUser = "AfterCommitUser"
        String rollbackUser = "RollbackUser"

        notificationService.reset()
        threadTracker.remove('AuditEventListener.onBatchProcessed')
        threadTracker.remove('GlobalStatsProjector.lastThread')
        threadTracker.remove("AsyncNotificationService.completed.${commitUser}")
        threadTracker.remove("AsyncNotificationService.completed.${rollbackUser}")

        double startBalance = summaryRepo.findById("GLOBAL").map { it.totalBalance }.orElse(0.0)

        when: "publikujemy event w transakcji, która sie COMMITUJE"
        transactionTemplate.executeWithoutResult {
            eventPublisher.publishEvent(new TransactionBatchProcessedEvent(
                    userName: commitUser,
                    totalBalance: 111.0,
                    transactionsCount: 2,
                    generatedReport: "commit-report"
            ))
        }

        then: "listenery AFTER_COMMIT uruchamiają sie asynchronicznie po commit"
        await().atMost(10, TimeUnit.SECONDS).until {
            Map audit = threadTracker.get('AuditEventListener.onBatchProcessed') as Map
            Map projector = threadTracker.get('GlobalStatsProjector.lastThread') as Map
            double balance = summaryRepo.findById("GLOBAL").map { it.totalBalance }.orElse(0.0)

            notificationService.getProcessedCount() == 1 &&
                    audit?.user == commitUser &&
                    projector?.user == commitUser &&
                    balance == (startBalance + 111.0d)
        }

        and: "resetujemy stan przed scenariuszem rollback"
        notificationService.reset()
        threadTracker.remove('AuditEventListener.onBatchProcessed')
        threadTracker.remove('GlobalStatsProjector.lastThread')
        threadTracker.remove("AsyncNotificationService.completed.${rollbackUser}")

        double beforeRollbackBalance = summaryRepo.findById("GLOBAL").map { it.totalBalance }.orElse(0.0)

        when: "publikujemy event, ale transakcja jest wycofywana"
        transactionTemplate.executeWithoutResult { status ->
            eventPublisher.publishEvent(new TransactionBatchProcessedEvent(
                    userName: rollbackUser,
                    totalBalance: 222.0,
                    transactionsCount: 3,
                    generatedReport: "rollback-report"
            ))
            status.setRollbackOnly()
        }

        and: "dajemy chwile na ewentualne (bledne) odpalenie asynchronicznych listenerow"
        sleep(1200)

        then: "przy ROLLBACK listenery AFTER_COMMIT nie uruchamiaja sie"
        notificationService.getProcessedCount() == 0
        threadTracker.get('AuditEventListener.onBatchProcessed') == null
        threadTracker.get('GlobalStatsProjector.lastThread') == null

        and: "projekcja GLOBAL nie zmienia sie po rollback"
        summaryRepo.findById("GLOBAL").map { it.totalBalance }.orElse(0.0) == beforeRollbackBalance
    }
}

