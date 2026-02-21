package pl.edu.praktyki.service

import spock.lang.Specification
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.ContextConfiguration
import pl.edu.praktyki.domain.Transaction
import java.time.LocalDate

@ContextConfiguration(classes = [FinancialAnalyticsService])
class FinancialAnalyticsSpec extends Specification {

    @Autowired
    FinancialAnalyticsService analyticsService

    def "powinien poprawnie obliczyć statystyki finansowe"() {
        given: "lista transakcji w PLN (już znormalizowanych)"
        def data = [
                new Transaction(id: "1", amountPLN: 5000.0, category: "Pensja", date: LocalDate.now()),
                new Transaction(id: "2", amountPLN: -100.0, category: "Jedzenie", date: LocalDate.now()),
                new Transaction(id: "3", amountPLN: -250.0, category: "Jedzenie", date: LocalDate.now()),
                new Transaction(id: "4", amountPLN: -1200.0, category: "Dom", date: LocalDate.now().minusDays(1)),
                new Transaction(id: "5", amountPLN: -50.0, category: "Rozrywka", date: LocalDate.now().minusDays(1))
        ]

        when: "liczymy bilans całkowity"
        def balance = analyticsService.calculateTotalBalance(data)

        then: "bilans wynosi 3400 (5000 - 100 - 250 - 1200 - 50)"
        balance == 3400.0

        when: "analizujemy wydatki wg kategorii"
        def spending = analyticsService.getSpendingByCategory(data)

        then: "kategoria Jedzenie sumuje się do 350"
        spending["Jedzenie"] == 350.0
        spending["Dom"] == 1200.0
        spending.size() == 3

        when: "szukamy najdroższej operacji"
        def expensive = analyticsService.getMostExpensiveTransaction(data)

        then: "jest to wydatek na Dom"
        expensive.category == "Dom"
        expensive.amountPLN == -1200.0
    }

    def "should return null when there are no transactions"() {
        given:
        List<Transaction> transactions = []

        when:
        def result = analyticsService.getTopSpendingCategory(transactions)

        then:
        result == null
    }

    def "should return null when there are no expenses"() {
        given:
        List<Transaction> transactions = [new Transaction(description: "Wypłata", amountPLN: 5000.0, category: "Wynagrodzenie", date: LocalDate.now())
        ]

        when:
        def result = analyticsService.getTopSpendingCategory(transactions)

        then:
        result == null
    }

    def "powinien wskazać kategorię z największymi wydatkami"() {
        given:
        def data = [
                new Transaction(amountPLN: -100.0, category: "Jedzenie"),
                new Transaction(amountPLN: -500.0, category: "Rozrywka"),
                new Transaction(amountPLN: -200.0, category: "Jedzenie")
        ]

        expect: "Rozrywka (500) jest większa niż suma Jedzenia (300)"
        analyticsService.getTopSpendingCategory(data) == "Rozrywka"
    }

    def "should return the category with highest spending"() {
        given:
        List<Transaction> transactions = [
                new Transaction(description: "Obiad", amountPLN: -50.0, category: "Jedzenie", date: LocalDate.now()),
                new Transaction(description: "Zakupy", amountPLN: -150.0, category: "Jedzenie", date: LocalDate.now()),
                new Transaction(description: "Paliwo", amountPLN: -100.0, category: "Transport", date: LocalDate.now()),
                new Transaction(description: "Kino", amountPLN: -30.0, category: "Rozrywka", date: LocalDate.now()),
        ]

        when:
        def result = analyticsService.getTopSpendingCategory(transactions)

        then:
        result == "Jedzenie"
    }

    def "should return correct category when there is only one expense category"() {
        given:
        List<Transaction> transactions = [
                new Transaction(description: "Bilet", amountPLN: -20.0, category: "Transport", date: LocalDate.now()),
                new Transaction(description: "Paliwo", amountPLN: -80.0, category: "Transport", date: LocalDate.now()),
        ]

        when:
        def result = analyticsService.getTopSpendingCategory(transactions)

        then:
        result == "Transport"
    }

    def "should ignore income transactions when determining top spending category"() {
        given:
        List<Transaction> transactions = [
                new Transaction(description: "Wypłata", amountPLN: 10000.0, category: "Wynagrodzenie", date: LocalDate.now()),
                new Transaction(description: "Obiad", amountPLN: -50.0, category: "Jedzenie", date: LocalDate.now()),
                new Transaction(description: "Paliwo", amountPLN: -200.0, category: "Transport", date: LocalDate.now()),
        ]

        when:
        def result = analyticsService.getTopSpendingCategory(transactions)

        then:
        result == "Transport"
    }
}