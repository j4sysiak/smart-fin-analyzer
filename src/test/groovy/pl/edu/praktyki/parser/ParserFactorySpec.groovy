package pl.edu.praktyki.parser

import spock.lang.Specification

class ParserFactorySpec extends Specification {

    def "powinien zwrócić odpowiedni parser zgodnie z zasadą Open/Closed"() {
        given: "różne typy plików"
        def csvFile = new File("wyciag.csv")
        def jsonFile = new File("dane.json")
        def unknownFile = new File("wirus.exe")

        when: "prosimy Fabrykę o parsery"
        def parser1 = ParserFactory.getParserForFile(csvFile)
        def parser2 = ParserFactory.getParserForFile(jsonFile)
        def parser3 = ParserFactory.getParserForFile(unknownFile)

        then: "otrzymujemy poprawne implementacje (Zasada Dependency Inversion)"
        // ZAMIAST: parser1 instanceof CsvTransactionParser
        parser1.class == CsvTransactionParser
        parser2.class == JsonTransactionParser

        and: "Wzorzec Null Object chroni nas przed nieznanymi formatami"
        parser3.class == UnsupportedFormatParser

        and: "wywołanie metody na nieznanym formacie jest bezpieczne i zwraca pustą listę"
        def result = parser3.parse(unknownFile)
        result.isEmpty() == true
    }

    def "powinien zwrócić CsvTransactionParser dla pliku .csv"() {
        given:
        def file = new File("test.csv")

        when:
        def parser = ParserFactory.getParserForFile(file)

        then:
        parser instanceof CsvTransactionParser
    }

    def "powinien zwrócić JsonTransactionParser dla pliku .json"() {
        given:
        def file = new File("test.json")

        when:
        def parser = ParserFactory.getParserForFile(file)

        then:
        parser instanceof JsonTransactionParser
    }

    def "powinien zwrócić UnsupportedFormatParser dla nieobsługiwanego formatu (Null Object)"() {
        given:
        def file = new File("test.xml")

        when:
        def parser = ParserFactory.getParserForFile(file)

        then: "zamiast wyjątku dostajemy bezpieczny Null Object"
        parser instanceof UnsupportedFormatParser
        parser.parse(file).isEmpty()
    }
}