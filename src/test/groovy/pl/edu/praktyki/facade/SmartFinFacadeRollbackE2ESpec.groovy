package pl.edu.praktyki.facade

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.support.TransactionTemplate
import pl.edu.praktyki.BaseIntegrationSpec
import pl.edu.praktyki.domain.TransactionDto
import pl.edu.praktyki.repository.FinancialSummaryRepository
import pl.edu.praktyki.repository.TransactionRepository
import pl.edu.praktyki.service.AsyncNotificationService
import pl.edu.praktyki.service.ThreadTracker

import java.time.LocalDate

class SmartFinFacadeRollbackE2ESpec extends BaseIntegrationSpec {

    @Autowired
    SmartFinFacade facade

    @Autowired
    TransactionTemplate transactionTemplate

    @Autowired
    TransactionRepository transactionRepository

    @Autowired
    FinancialSummaryRepository summaryRepository

    @Autowired
    AsyncNotificationService notificationService

    @Autowired
    ThreadTracker threadTracker

    def "nie powinien uruchomic listenerow AFTER_COMMIT ani zapisac danych gdy transakcja fasady zostanie wycofana"() {
        given: "wejscie i czysty stan po poprzednich testach"
        String user = "FacadeRollbackUser"
        String originalId = "E2E-ROLLBACK-1"

        notificationService.reset()
        threadTracker.remove('AuditEventListener.onBatchProcessed')
        threadTracker.remove('GlobalStatsProjector.lastThread')
        threadTracker.remove("AsyncNotificationService.completed.${user}")

        double startGlobal = summaryRepository.findById("GLOBAL").map { it.totalBalance }.orElse(0.0)

        def data = [
                new TransactionDto(
                        id: originalId,
                        amount: 333.0,
                        currency: "PLN",
                        category: "ROLLBACK",
                        description: "rollback e2e",
                        date: LocalDate.now()
                )
        ]

        when: "wykonujemy pełną ścieżkę fasady, ale na końcu oznaczamy transakcję do rollback"
        String report = transactionTemplate.execute { status ->
            String generated = facade.processAndGenerateReport(user, data, [])
            status.setRollbackOnly()
            return generated
        }

        and: "dajemy chwile na ewentualne (błędne) odpalenie async listenerów"
        sleep(1200)

        then: "raport może zostać wygenerowany w pamięci, ale transakcja jest finalnie wycofana"
        report != null
        report.contains("FACADE ROLLBACK USER") || report.toUpperCase().contains("FACADE")

        and: "rekord biznesowy nie powinien istnieć po rollback"
        !transactionRepository.findByOriginalIdAndOwnerUsername(originalId, user).present

        and: "listenerzy AFTER_COMMIT nie uruchamiają się"
        threadTracker.get('AuditEventListener.onBatchProcessed') == null
        threadTracker.get('GlobalStatsProjector.lastThread') == null
        threadTracker.get("AsyncNotificationService.completed.${user}") == null

        and: "projekcja GLOBAL pozostaje bez zmian"
        summaryRepository.findById("GLOBAL").map { it.totalBalance }.orElse(0.0) == startGlobal
    }
}

