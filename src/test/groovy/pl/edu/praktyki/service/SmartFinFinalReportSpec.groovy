package pl.edu.praktyki.service

import spock.lang.Specification
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.ContextConfiguration
import pl.edu.praktyki.domain.Transaction
import java.time.LocalDate

// Ładujemy oba serwisy do jednego kontekstu
@ContextConfiguration(classes = [FinancialAnalyticsService, ReportGeneratorService])
class SmartFinFinalReportSpec extends Specification {

    @Autowired
    FinancialAnalyticsService analyticsService

    @Autowired
    ReportGeneratorService reportGeneratorService

    def "powinien przeprowadzić pełny proces: od surowych danych do gotowego raportu"() {
        given: "1. Lista surowych transakcji (już po przeliczeniu na PLN)"
        def transactions = [
                new Transaction(id: "T1", amountPLN: 6000.0, category: "Praca", description: "Wypłata", date: LocalDate.now()),
                new Transaction(id: "T2", amountPLN: -2200.0, category: "Dom", description: "Czynsz i media", date: LocalDate.now()),
                new Transaction(id: "T3", amountPLN: -450.0, category: "Jedzenie", description: "Zakupy tydzień 1", date: LocalDate.now()),
                new Transaction(id: "T4", amountPLN: -300.0, category: "Jedzenie", description: "Restauracja", date: LocalDate.now()),
                new Transaction(id: "T5", amountPLN: -150.0, category: "Rozrywka", description: "Kino i popcorn", date: LocalDate.now()),
                new Transaction(id: "T6", amountPLN: -120.0, category: "Zdrowie", description: "Apteka", date: LocalDate.now())
        ]

        when: "2. Wykorzystujemy FinancialAnalyticsService do wyciągnięcia statystyk"
        def total = analyticsService.calculateTotalBalance(transactions)
        def spending = analyticsService.getSpendingByCategory(transactions)
        def top = analyticsService.getTopSpendingCategory(transactions)

        and: "3. Przygotowujemy mapę parametrów dla generatora"
        def statsMap = [
                totalBalance: total,
                topCategory: top,
                spendingMap: spending
        ]

        and: "4. Generujemy finałowy raport"
        String finalReport = reportGeneratorService.generateMonthlyReport("Twoje Imię", statsMap)

        then: "5. Weryfikujemy czy raport jest kompletny i poprawny"
        println finalReport // WYDRUK NA KONSOLĘ

        finalReport.contains("TWOJE IMIĘ")
        finalReport.contains("Bilans całkowity:  2780.0 PLN")
        finalReport.contains("Główny wydatek:    Dom")

        and: "kategorie są poprawnie zsumowane (Jedzenie: 450 + 300 = 750)"
        finalReport.contains("Jedzenie        : 750.00 PLN")

        and: "status finansowy jest poprawny"
        finalReport.contains("Status: NA PLUSIE")
    }
}