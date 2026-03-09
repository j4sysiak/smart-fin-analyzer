package pl.edu.praktyki.domain

import spock.lang.Specification
import java.time.LocalDate

class BuilderSpec extends Specification {

    def "powinien zbudować obiekt ReportConfig używając wygenerowanego Buildera"() {
        when: "używamy Buildera (wygenerowanego przez AST w czasie kompilacji)"
        def config = ReportConfig1.builder()
                .title("Raport Roczny 2025")
                .startDate(LocalDate.of(2025, 1, 1))
                .endDate(LocalDate.of(2025, 12, 31))
                .includeCharts(true)
                .exportFormat("PDF")
                // recipientEmail celowo pomijamy - będzie null
                .build()

        then: "obiekt jest poprawnie zbudowany"
        config.title == "Raport Roczny 2025"
        config.includeCharts == true
        config.recipientEmail == null // Wartość domyślna

        and: "toString ładnie wypisuje zawartość"
        println config.toString()
        config.toString().contains("PDF")
    }

    def "powinien odrzucić błędne daty (Wyzwanie)"() {
        when:
        def config = ReportConfig1.builder()
                .startDate(LocalDate.of(2025, 12, 31))
                .endDate(LocalDate.of(2025, 1, 1)) // BŁĄD: Koniec jest przed początkiem
                .build()

        then:
        // Wywołujemy metodę isValid() i sprawdzamy czy zwróciła false
        config.toValid() == false
    }

    def "powinien zbudować obiekt ReportConfig używając InitializerStrategy"() {
        when: "używamy nowej nazwy metody i przekazujemy to do konstruktora (bez .build() na końcu!)"
        def config = new ReportConfig2(
                ReportConfig2.createInitializer() // <--- NASZA NOWA METODA
                        .title("Raport Roczny 2026")
                        .startDate(LocalDate.of(2026, 1, 1))
                        .endDate(LocalDate.of(2026, 12, 31))
                        .includeCharts(true)
                        .exportFormat("PDF")
                        .recipientEmail(null)
        ) // <--- Tu zamykamy konstruktor

        then: "obiekt jest poprawnie zbudowany"
        config.title == "Raport Roczny 2026"
        config.includeCharts == true
        config.recipientEmail == null

        and: "metoda walidująca działa"
        config.toValid() == true
    }
}