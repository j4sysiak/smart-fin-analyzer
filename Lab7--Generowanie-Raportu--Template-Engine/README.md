Lab 7
-----

Faza 3: Krok 7 – Generowanie Raportu (Template Engine)

Teraz, gdy mamy już dane i potrafimy je analizować, czas na prezentację. 
Wykorzystamy `SimpleTemplateEngine`, aby stworzyć profesjonalne podsumowanie finansowe.

7.1. Serwis Generatora Raportów (ReportGeneratorService.groovy)
Stwórz plik `src/main/groovy/pl/edu/praktyki/service/ReportGeneratorService.groovy`:


```groovy
package pl.edu.praktyki.service

import org.springframework.stereotype.Service
import groovy.text.SimpleTemplateEngine

@Service
class ReportGeneratorService {

    private final engine = new SimpleTemplateEngine()

    /**
     * Generuje raport tekstowy/HTML na podstawie danych analitycznych.
     */
    String generateMonthlyReport(String userName, Map<String, Object> stats) {
        def templateText = '''
            =========================================
            RAPORT FINANSOWY DLA: ${user.toUpperCase()}
            =========================================
            Data wygenerowania: ${reportDate}
            
            PODSUMOWANIE:
            -----------------------------------------
            Bilans całkowity:  ${totalBalance} PLN
            Główny wydatek:    ${topCategory}
            
            WYDATKI WG KATEGORII:
            <% spendingMap.each { category, amount -> %>
            - ${category.padRight(15)} : ${amount.setScale(2, BigDecimal.ROUND_HALF_UP)} PLN
            <% } %>
            -----------------------------------------
            Status: ${totalBalance >= 0 ? 'NA PLUSIE' : 'DEFICYT'}
            =========================================
        '''.stripIndent()

        def binding = [
            user: userName,
            reportDate: java.time.LocalDate.now(),
            totalBalance: stats.totalBalance,
            topCategory: stats.topCategory,
            spendingMap: stats.spendingMap
        ]

        return engine.createTemplate(templateText).make(binding).toString()
    }
}
```


7.2. Test Generatora (ReportGeneratorSpec.groovy)
Stwórz plik `src/test/groovy/pl/edu/praktyki/service/ReportGeneratorSpec.groovy`:

```groovy
package pl.edu.praktyki.service

import spock.lang.Specification
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.ContextConfiguration

@ContextConfiguration(classes = [ReportGeneratorService])
class ReportGeneratorSpec extends Specification {

    @Autowired
    ReportGeneratorService reportGeneratorService

    def "powinien wygenerować czytelny raport finansowy"() {
        given: "przygotowane statystyki"
        def stats = [
                totalBalance: 1500.50,
                topCategory: "Elektronika",
                spendingMap: ["Jedzenie": 300.0, "Elektronika": 1200.0]
        ]

        when: "generujemy raport"
        def report = reportGeneratorService.generateMonthlyReport("Jan Kowalski", stats)

        println report // Zobaczysz wynik w konsoli testu!

        then: "raport zawiera kluczowe informacje"
        report.contains("JAN KOWALSKI")
        report.contains("Bilans całkowity:  1500.50 PLN")
        report.contains("- Jedzenie")
        report.contains("Status: NA PLUSIE")
    }

    def "powinien wyświetlić status NA MINUSIE dla ujemnego bilansu"() {
        given: "statystyki z ujemnym bilansem"
        def stats = [
                totalBalance: -250.75,
                topCategory : "Jedzenie",
                spendingMap : ["Jedzenie": 300.0, "Transport": 150.0]
        ]

        when: "generujemy raport"
        def report = reportGeneratorService.generateMonthlyReport("Anna Nowak", stats)

        println report // Zobaczysz wynik w konsoli testu!

        then: "raport zawiera status DEFICYT"
        report.contains("ANNA NOWAK")
        report.contains("-250.75 PLN")
        report.contains("Status: DEFICYT")

        and: "wypisz raport"
        System.out.println("=== RAPORT (ujemny bilans) ===")
        System.out.println(report)
        System.out.println("=== KONIEC ===")
    }

    def "powinien obsłużyć pustą mapę wydatków"() {
        given: "statystyki bez wydatków"
        def stats = [
                totalBalance: 0.0,
                topCategory : "Brak",
                spendingMap : [:]
        ]

        when: "generujemy raport"
        def report = reportGeneratorService.generateMonthlyReport("Piotr Wiśniewski", stats)

        println report // Zobaczysz wynik w konsoli testu!

        then: "raport jest wygenerowany poprawnie"
        report.contains("PIOTR WIŚNIEWSKI")
        report.contains("0.0 PLN")
        !report.contains("- ")

        and: "wypisz raport"
        System.out.println("=== RAPORT (pusta mapa) ===")
        System.out.println(report)
        System.out.println("=== KONIEC ===")
    }

    def "powinien zawierać wszystkie kategorie wydatków w raporcie"() {
        given: "statystyki z wieloma kategoriami"
        def stats = [
                totalBalance: 500.0,
                topCategory : "Rozrywka",
                spendingMap : ["Jedzenie": 200.0, "Rozrywka": 450.0, "Transport": 100.0, "Zdrowie": 50.0]
        ]

        when: "generujemy raport"
        def report = reportGeneratorService.generateMonthlyReport("Maria Zielińska", stats)

        println report // Zobaczysz wynik w konsoli testu!

        then: "raport zawiera wszystkie kategorie"
        report.contains("- Jedzenie")
        report.contains("- Rozrywka")
        report.contains("- Transport")
        report.contains("- Zdrowie")
        report.contains("Główny wydatek:    Rozrywka")

        and: "wypisz raport"
        System.out.println("=== RAPORT (wiele kategorii) ===")
        System.out.println(report)
        System.out.println("=== KONIEC ===")
    }
}
```


Wyzwanie Finałowe Fazy 3:
-------------------------   

"Wielki Finał":

Weź listę surowych transakcji.
Użyj `FinancialAnalyticsService`, aby wyciągnąć z nich mapę statystyk.
Przekaż tę mapę do `ReportGeneratorService`.
Wypisz gotowy raport na ekran.


Rozwizanie:
-----------

To jest moment, w którym wszystkie klocki, które budowaliśmy przez ostatnie dni, łączą się w jeden działający organizm. 
Ten "Wielki Finał" Fazy 3 pokazuje prawdziwą synergię między logiką biznesową a warstwą prezentacji.

Oto kompletne rozwiązanie, które możesz uruchomić jako test integracyjny.

Stwórz ten plik w `src/test/groovy/pl/edu/praktyki/service/SmartFinFinalReportSpec.groovy`. 

Zwróć uwagę, jak Spock elegancko zarządza przepływem danych między serwisami.

```groovy
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
```

Co dokładnie się tutaj wydarzyło? (Analiza Architektoniczna)

Data Flow (Przepływ danych): 
Dane wejściowe (List<Transaction>) zostały przetworzone przez algorytmy analityczne, które wyprodukowały skondensowane wyniki (statsMap). 
Te wyniki zostały następnie "wstrzyknięte" do szablonu tekstowego. 
To klasyczny wzorzec ETL (Extract, Transform, Load) w mikroskali.

Separacja Odpowiedzialności:
1. Transaction wie, jak przechowywać dane.
2. AnalyticsService wie, jak liczyć.
3. ReportGeneratorService wie, jak wyświetlać.
4. Spec (Test) wie, jak to wszystko połączyć.

Groovy Power: 
Zauważ, jak mało kodu potrzebowaliśmy, aby zsumować wydatki na jedzenie z dwóch różnych transakcji 
(zrobiło to groupBy i sum wewnątrz serwisu analitycznego).

Jak to uruchomić?
Upewnij się, że masz wszystkie 3 klasy (Transaction, FinancialAnalyticsService, ReportGeneratorService).
Uruchom test `SmartFinFinalReportSpec`.
Spójrz w konsolę IntelliJ (zakładka Output). 
Powinieneś zobaczyć pięknie sformatowany raport z ramkami zrobionymi ze znaków =.

Co dalej? (Faza 4 - Ostatnia)

Zbudowałeś silnik. Teraz musimy dać użytkownikowi "kierownicę".

W Fazie 4: Narzędzia CLI, stworzymy główną klasę startową aplikacji, która:

Użyje `CliBuilder`, aby przyjąć imię użytkownika i ścieżkę do pliku z transakcjami.

Uruchomi cały rurociąg (Import -> Waluty -> Reguły -> Analityka -> Raport).

Zapisze gotowy raport do pliku .txt.

