package pl.edu.praktyki.report.trait


import spock.lang.Specification

class ReportTraitSpec extends Specification {

    def "powinien wykorzystać metody z obu traitów"() {
        given:
        def report = new FinanceReport()

        when: "wywołujemy metodę z ReportGenerator"
        report.generateReport()

        and: "wywołujemy metodę z AuditLog"
        report.audit("Sprawdzenie poprawności raportu")

        then: "obie funkcjonalności działają"
        report.hasAuditEntry("Sprawdzenie poprawności raportu")
        // Sprawdzamy czy metoda z traita faktycznie istnieje
        report.respondsTo("generateReport") != null
    }
}