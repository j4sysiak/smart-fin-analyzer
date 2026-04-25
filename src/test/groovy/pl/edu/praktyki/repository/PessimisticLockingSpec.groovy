package pl.edu.praktyki.repository

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationEventPublisher
import pl.edu.praktyki.BaseIntegrationSpec
import pl.edu.praktyki.event.TransactionBatchProcessedEvent
import java.util.concurrent.TimeUnit
import static org.awaitility.Awaitility.await

class PessimisticLockingSpec extends BaseIntegrationSpec {

    @Autowired ApplicationEventPublisher eventPublisher
    @Autowired FinancialSummaryRepository summaryRepo

    def "powinien bezbłędnie zsumować 50 równoległych paczek danych (Stress Test Blokady)"() {
        given: "czyścimy stan początkowy"
        def summary = summaryRepo.findById("GLOBAL").get()
        def startBalance = summary.totalBalance
        int numberOfBatches = 50
        BigDecimal amountPerBatch = 10.0

        when: "bombardujemy system 50 zdarzeniami naraz"
        (1..numberOfBatches).each {
            eventPublisher.publishEvent(new TransactionBatchProcessedEvent(
                    userName: "StressUser",
                    totalBalance: amountPerBatch,
                    transactionsCount: 1
            ))
        }

        then: "Czekamy, aż wszystkie 50 asynchronicznych wątków przepchnie się przez blokadę bazy"
        await().atMost(60, TimeUnit.SECONDS).until { // Zwiększamy do 60s na wypadek, gdyby baza była bardzo obciążona
            summaryRepo.findById("GLOBAL").get().totalBalance == startBalance + (numberOfBatches * amountPerBatch)
        }

        expect: "Suma jest idealna, co do grosza"
        summaryRepo.findById("GLOBAL").get().totalBalance == startBalance + 500.0
    }
}