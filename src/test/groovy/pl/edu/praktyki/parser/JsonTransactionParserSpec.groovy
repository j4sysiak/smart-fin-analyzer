package pl.edu.praktyki.parser

import spock.lang.Specification
import java.nio.file.Files

class JsonTransactionParserSpec extends Specification {

    def "powinien sparsować plik JSON na listę transakcji"() {
        given:
        def jsonContent = '''[
  {"id":"1","amount":100,"currency":"EUR","category":"Jedzenie","description":"Obiad","date":"2025-07-22"},
  {"id":"2","amount":-50,"currency":"USD","category":"Rozrywka","description":"Kino","date":"2025-07-22"}
]'''

        def tempFile = Files.createTempFile("test", ".json").toFile()
        tempFile.text = jsonContent
        tempFile.deleteOnExit()

        def parser = new JsonTransactionParser()

        when:
        def result = parser.parse(tempFile)

        then:
        result.size() == 2
        result[0].id == "1"
        result[0].amount == 100
        result[0].currency == "EUR"
        result[1].description == "Kino"
    }

    def "powinien zwrócić pustą listę dla pustej tablicy JSON"() {
        given:
        def tempFile = Files.createTempFile("empty", ".json").toFile()
        tempFile.text = "[]"
        tempFile.deleteOnExit()

        def parser = new JsonTransactionParser()

        when:
        def result = parser.parse(tempFile)

        then:
        result.isEmpty()
    }
}