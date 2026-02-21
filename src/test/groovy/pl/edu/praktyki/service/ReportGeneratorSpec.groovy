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