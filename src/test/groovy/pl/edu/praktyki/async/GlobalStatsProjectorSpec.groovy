package pl.edu.praktyki.async

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationEventPublisher
import pl.edu.praktyki.BaseIntegrationSpec
import pl.edu.praktyki.event.TransactionBatchProcessedEvent
import pl.edu.praktyki.service.ThreadTracker
import pl.edu.praktyki.repository.FinancialSummaryRepository
import static org.awaitility.Awaitility.await
import java.util.concurrent.TimeUnit

class GlobalStatsProjectorSpec extends BaseIntegrationSpec {

    @Autowired
    ApplicationEventPublisher eventPublisher

    @Autowired
    ThreadTracker threadTracker

    @Autowired
    FinancialSummaryRepository summaryRepo

    def setup() {
        // reset the lastThread marker before each test - use a non-null placeholder because ConcurrentHashMap disallows nulls
        threadTracker.put('GlobalStatsProjector.lastThread', 'PENDING')
    }

    def "projektor aktualizuje global summary i zapisuje watek wykonujacy"() {
        given:
        def ev = new TransactionBatchProcessedEvent(userName: 'specUser', totalBalance: 500.0, transactionsCount: 3)

        when:
        eventPublisher.publishEvent(ev)

        then: "watek zostal zarejestrowany w ThreadTracker"
        await().atMost(10, TimeUnit.SECONDS).until {
            threadTracker.get('GlobalStatsProjector.lastThread') != 'PENDING'
        }

        and: "zaktualizowano wpis w tabeli financial_summary"
        await().atMost(10, TimeUnit.SECONDS).until {
            def opt = summaryRepo.findById('GLOBAL')
            opt.present && (opt.get().totalBalance?.toBigDecimal() >= 500.0.toBigDecimal())
        }

        where:
        // no data-driven inputs
        _ << [0]
    }
}

