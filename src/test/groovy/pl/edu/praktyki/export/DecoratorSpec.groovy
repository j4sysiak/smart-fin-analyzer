package pl.edu.praktyki.export

import spock.lang.Specification
import pl.edu.praktyki.domain.Transaction

class DecoratorSpec extends Specification {

    def tx = new Transaction(id: "T-123", amountPLN: 450.50)

    def "powinien użyć podstawowego eksportera"() {
        given:
        def exporter = new BasicExporter()

        expect:
        exporter.exportHeader() == "--- LISTA TRANSAKCJI ---"
        exporter.exportRow(tx) == "TX: T-123 | KWOTA: 450.50"
    }

    def "powinien udekorować eksport tagami HTML"() {
        given: "owijamy BasicExporter w HtmlDecorator"
        def exporter = new HtmlExporterDecorator(new BasicExporter())

        expect:
        exporter.exportHeader() == "<h1>--- LISTA TRANSAKCJI ---</h1>"
        exporter.exportRow(tx) == "<tr><td>TX: T-123 | KWOTA: 450.50</td></tr>"
    }

    def "powinien użyć obu dekoratorów naraz (HTML + Cenzura)"() {
        given: "podwójna matrioszka: Html(Confidential(Basic))"
        def exporter = new HtmlExporterDecorator(
                new ConfidentialExporterDecorator(
                        new BasicExporter()
                )
        )

        when:
        def result = exporter.exportRow(tx)

        then: "dane są zacenzurowane ORAZ owinięte w HTML"
        result == "<tr><td>TX: T-***.** | KWOTA: ***.**</td></tr>"

        and: "header działa, mimo że Confidential go nie nadpisał (Magia @Delegate)"
        exporter.exportHeader() == "<h1>--- LISTA TRANSAKCJI ---</h1>"
    }
}