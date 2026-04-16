package pl.edu.praktyki.event

import pl.edu.praktyki.BaseIntegrationSpec
import org.springframework.beans.factory.annotation.Autowired
import pl.edu.praktyki.domain.Transaction
import pl.edu.praktyki.facade.SmartFinFacade
import pl.edu.praktyki.repository.FinancialSummaryRepository

import static org.awaitility.Awaitility.await
import java.util.concurrent.TimeUnit

class CqrsEventSpec extends BaseIntegrationSpec {

    @Autowired SmartFinFacade facade
    @Autowired FinancialSummaryRepository summaryRepo

    def "powinien asynchronicznie zaktualizować globalne statystyki przez eventy"() {
        given: "obecny bilans w tabeli financial_summary"
        def startBalance = summaryRepo.findById("GLOBAL")
                .map{it.totalBalance}
                .orElse(0.0)

        when: "importujemy nową paczkę danych przez Fasadę"
        def data = [new Transaction(id: "CQRS-1", amount: 100, currency: "PLN")]
        facade.processAndGenerateReport("CqrsUser", data, [])

        then: "Czekamy, aż SummaryProjectionListener złapie event i zaktualizuje tabelę"
        await().atMost(5, TimeUnit.SECONDS).until {
            summaryRepo.findById("GLOBAL").get().totalBalance == startBalance + 100.0
        }
    }
}