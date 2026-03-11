package pl.edu.praktyki.report

import spock.lang.Specification

class ReportTemplateSpec extends Specification {

    def "powinien poprawnie wygenerować raport HTML"() {
        given: "nasz szablon HTML"
        def report = new HtmlReport()

        when: "wywołujemy szablon"
        // Zwróć uwagę, że nie wołamy metod pośrednich, tylko szkielet
        report.generateReport()

        then: "wszystko zadziałało w narzuconej kolejności"
        // Tutaj w prawdziwym teście sprawdzilibyśmy np. plik na dysku
        // lub stan obiektu
        noExceptionThrown()
    }
}