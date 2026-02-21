Lab 6
-----

To jest serce analityczne naszej aplikacji. 
W tym kroku wykorzystamy najpotężniejsze funkcje kolekcji Groovy’ego, aby zamienić listę tysięcy transakcji w konkretne odpowiedzi biznesowe.

Krok 6: Agregacja i Statystyki (Financial Analytics)
----------------------------------------------------

Cel: 
Stworzenie serwisu, który na podstawie przeliczonych kwot (amountPLN) wygeneruje raporty: sumę wydatków na kategorię, najdroższy dzień w miesiącu oraz ogólny bilans.

6.1. Serwis Analityczny (FinancialAnalyticsService.groovy)

Ten serwis nie potrzebuje bazy danych – operuje na listach obiektów w pamięci, co czyni go niesamowicie szybkim.

Stwórz plik src/main/groovy/pl/edu/praktyki/service/FinancialAnalyticsService.groovy:

code
Groovy
download
content_copy
expand_less
package pl.edu.praktyki.service

import org.springframework.stereotype.Service
import pl.edu.praktyki.domain.Transaction

@Service
class FinancialAnalyticsService {

    /**
     * Oblicza całkowity bilans (Wpływy - Wydatki) w PLN.
     */
    BigDecimal calculateTotalBalance(List<Transaction> transactions) {
        // Magia Groovy: suma pola amountPLN ze wszystkich obiektów
        return transactions*.amountPLN.sum() ?: 0.0
    }

    /**
     * Grupuje wydatki według kategorii i sumuje je.
     * Zwraca Mapę: [Kategoria: SumaWydatków]
     */
    Map<String, BigDecimal> getSpendingByCategory(List<Transaction> transactions) {
        return transactions
            .findAll { it.isExpense() } // Tylko wydatki (ujemne)
            .groupBy { it.category }    // Grupowanie w Mapę [Category: List<Transaction>]
            .collectEntries { category, list -> 
                [category, list*.amountPLN.sum().abs()] // Sumujemy i bierzemy wartość bezwzględną
            }
    }

    /**
     * Znajduje transakcję o największej kwocie wydatku.
     */
    Transaction getMostExpensiveTransaction(List<Transaction> transactions) {
        // Szukamy minimum, bo wydatki są ujemne (np. -5000 < -100)
        return transactions.findAll { it.isExpense() }.min { it.amountPLN }
    }

    /**
     * Podsumowanie dzienne: ile wydano każdego dnia.
     */
    Map<java.time.LocalDate, BigDecimal> getDailySpending(List<Transaction> transactions) {
        return transactions
            .findAll { it.isExpense() }
            .groupBy { it.date }
            .collectEntries { date, list -> [date, list*.amountPLN.sum().abs()] }
            .sort() // Sortowanie po dacie (kluczu mapy)
    }
}
6.2. Test Analityki (FinancialAnalyticsSpec.groovy)

Sprawdźmy, czy nasze algorytmy poprawnie interpretują dane finansowe.

Stwórz plik src/test/groovy/pl/edu/praktyki/service/FinancialAnalyticsSpec.groovy:

code
Groovy
download
content_copy
expand_less
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
}
Dlaczego to jest "Senior Level Groovy"?

collectEntries: To jedna z najbardziej eleganckich metod w Groovy. Pozwala w jednym kroku przekształcić jedną mapę w drugą (np. zamienić listę transakcji na ich sumę).

Spread Operator (*.): Zapis list*.amountPLN błyskawicznie tworzy listę samych liczb, gotową do metody .sum(). W Javie to 3 linie kodu ze Streamami.

abs(): Używamy wartości bezwzględnej w raporcie, bo biznes woli widzieć "Wydałeś 350 zł na jedzenie" niż "Wydałeś -350 zł".

Implicit Return: Zauważ, że w metodach nie używamy słowa return tam, gdzie wynik jest oczywisty (ostatnia linia). To standard w Groovy.

Twoje zadanie:

Zaimplementuj serwis i uruchom test.

Wyzwanie: Dodaj do serwisu metodę getTopSpendingCategory(List<Transaction> transactions), która zwróci nazwę kategorii (String), na którą wydano najwięcej pieniędzy.
Podpowiedź: Użyj getSpendingByCategory(transactions).max { it.value }.key.

Daj znać, gdy testy przejdą! W następnym kroku (Krok 7) połączymy wszystko w Finałowy Raport HTML/PDF, używając silnika szablonów, który poznałeś w Lab 15. Twoja aplikacja zacznie generować profesjonalne dokumenty! 📄🚀