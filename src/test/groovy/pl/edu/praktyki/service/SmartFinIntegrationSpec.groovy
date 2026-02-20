package pl.edu.praktyki.service

import spock.lang.Specification
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.ContextConfiguration
import pl.edu.praktyki.domain.Transaction
import java.time.LocalDate

// Ładujemy OBA serwisy do kontekstu Springa
// ContextConfiguration: Mówimy Springowi:
// "Stwórz obiekty klas TransactionIngesterService oraz TransactionRuleService
// i powiąż je ze sobą (wstrzyknij jeden w drugi)".
@ContextConfiguration(classes = [TransactionIngesterService, TransactionRuleService])
class SmartFinIntegrationSpec extends Specification {

    @Autowired
    TransactionIngesterService pipelineService

    def "powinien zaimportować transakcje wielowątkowo i natychmiast oznaczyć je dynamicznymi tagami"() {
        given: "dwie paczki transakcji (np. z dwóch różnych banków)"
        def bankA = [
                new Transaction(id: "A1", date: LocalDate.now(), amount: 5000.0, category: "Praca", description: "Wypłata"),
                new Transaction(id: "A2", date: LocalDate.now(), amount: -15.0, category: "Jedzenie", description: "Kawa")
        ]

        def bankB = [
                new Transaction(id: "B1", date: LocalDate.now(), amount: -2500.0, category: "Dom", description: "Czynsz"),
                new Transaction(id: "B2", date: LocalDate.now(), amount: -45.0, category: "Rozrywka", description: "Netflix")
        ]

        and: "zestaw reguł biznesowych zdefiniowanych przez użytkownika"
        def myRules = [
                "if (amount > 0) addTag('INCOME')",
                "if (amount < -1000) addTag('HIGH_EXPENSE')",
                "if (description.contains('Netflix')) addTag('SUBSCRIPTION')"
        ]

        when: "uruchamiamy główny rurociąg przetwarzający dane równolegle"
        // Przekazujemy listę list (bankA, bankB) oraz nasze reguły
        def processedData = pipelineService.ingestAndApplyRules([bankA, bankB], myRules)

        then: "mamy wszystkie 4 transakcje w jednej płaskiej liście"
        processedData.size() == 4

        and: "Wypłata została rozpoznana jako przychód"
        def incomeTx = processedData.find { it.id == "A1" }
        incomeTx.tags.contains("INCOME")

        and: "Czynsz został oznaczony jako wysoki wydatek"
        def rentTx = processedData.find { it.id == "B1" }
        rentTx.tags.contains("HIGH_EXPENSE")

        and: "Netflix został rozpoznany jako subskrypcja"
        def netflixTx = processedData.find { it.id == "B2" }
        netflixTx.tags.contains("SUBSCRIPTION")

        and: "Kawa nie dostała żadnego tagu (żadna reguła nie pasuje)"
        def coffeeTx = processedData.find { it.id == "A2" }
        coffeeTx.tags.isEmpty()
    }
}