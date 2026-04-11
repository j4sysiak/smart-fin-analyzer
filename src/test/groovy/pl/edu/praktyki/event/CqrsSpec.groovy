package pl.edu.praktyki.event

import pl.edu.praktyki.BaseIntegrationSpec
import org.springframework.beans.factory.annotation.Autowired
import pl.edu.praktyki.facade.SmartFinFacade
import pl.edu.praktyki.domain.Transaction
import pl.edu.praktyki.repository.FinancialSummaryRepository
import static org.awaitility.Awaitility.await
import java.util.concurrent.TimeUnit
import java.time.LocalDate

class CqrsSpec extends BaseIntegrationSpec {

    @Autowired SmartFinFacade facade
    @Autowired FinancialSummaryRepository summaryRepo

    def "powinien asynchronicznie zaktualizować tabelę statystyk po zakończeniu importu"() {
        given: "obecny stan statystyk w bazie (bezpieczne pobranie)"
        def stats = summaryRepo.findById("GLOBAL")
                .orElse(new pl.edu.praktyki.repository.FinancialSummaryEntity(totalBalance: 0.0))
        def initialBalance = stats.totalBalance

        and: "nowa paczka danych na 1000 PLN"
        def data = [new Transaction(id: "CQRS-TEST", amount: 1000, currency: "PLN", category: "Test", date: LocalDate.now())]

        when: "wykonujemy proces"
        facade.processAndGenerateReport("CqrsTester", data, [])

        then: "Czekamy, aż listener zaktualizuje tabelę (dłuższy timeout dla local-pg)"
        def localPg = Boolean.getBoolean('local.pg')
        def timeoutSeconds = localPg ? 60 : 30
        await().atMost(timeoutSeconds, TimeUnit.SECONDS).until {
            def current = summaryRepo.findById("GLOBAL")
                    .orElse(null)
            if (current == null) return false
            // totalBalance to BigDecimal — porównujemy precyzyjnie (>= oczekiwanej wartości)
            def expected = initialBalance?.add(new BigDecimal("1000.0")) ?: new BigDecimal("1000.0")
            return current.totalBalance.compareTo(expected) >= 0
        }
    }
}