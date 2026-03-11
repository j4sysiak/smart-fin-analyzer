package pl.edu.praktyki.report

import spock.lang.Specification

class CsvReportSpec extends Specification {

    def "powinien poprawnie wygenerować raport CSV z przecinkami zamiast kropek"() {
        given: "instancja raportu CSV"
        def csvReport = new CsvReport()

        when: "uruchamiamy cały proces szablonowy"
        // Zauważ, że wołamy metodę finalną z klasy bazowej,
        // a ona wewnątrz używa naszych metod z CsvReport
        csvReport.generateReport()

        then: "zwracamy wynik do weryfikacji"
        // Aby ułatwić sobie testowanie, możemy "wyłapać" co metoda saveReport wypisała
        // albo dodać do klasy CsvReport metodę zwracającą wynik

        // Zróbmy małą sprytną weryfikację:
        def rawData = csvReport.fetchData()
        def processedData = csvReport.processData(rawData)

        processedData.contains("1,500,00,2026-03-10") // Sprawdzamy czy kropka zmieniła się w przecinek
        processedData.startsWith("ID,KWOTA,DATA")
    }

    def "powinien wypisać raport na konsolę"() {
        given:
        def csvReport = new CsvReport()
        def out = new ByteArrayOutputStream()
        System.setOut(new PrintStream(out)) // Przekierowanie konsoli do strumienia

        when:
        csvReport.saveReport("MOCK_REPORT")

        then:
        out.toString().contains("Zapisuję raport CSV do pliku: MOCK_REPORT")

        cleanup:
        System.setOut(System.out) // Koniecznie przywróć standardową konsolę!

    }

}