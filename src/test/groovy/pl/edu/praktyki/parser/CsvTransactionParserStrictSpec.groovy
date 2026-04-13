package pl.edu.praktyki.parser

import spock.lang.Specification

class CsvTransactionParserStrictSpec extends Specification {

    def "strict mode should throw on malformed lines"() {
        given:
        def parser = new CsvTransactionParser(true)
        def tmp = File.createTempFile('csvtest','txt')
        tmp.text = "id,amount,currency,category,description,date\nBADLINE,abc,PLN,Test,Opis,not-a-date\n"

        when:
        parser.parse(tmp)

        then:
        def e = thrown(Exception)
        e instanceof IllegalArgumentException || e instanceof java.time.format.DateTimeParseException || e instanceof NumberFormatException

        cleanup:
        tmp.delete()
    }

    def "lenient mode should skip malformed lines and parse valid ones"() {
        given:
        def parser = new CsvTransactionParser(false)
        def tmp = File.createTempFile('csvtest','txt')
        tmp.text = "id,amount,currency,category,description,date\nT1,100.00,PLN,Test,Opis,2026-03-01\nBADLINE,abc,PLN,Test,Opis,not-a-date\n"

        when:
        def list = parser.parse(tmp)

        then:
        list.size() == 1
        list[0].id == 'T1'
        list[0].amount == 100.00.toBigDecimal()

        cleanup:
        tmp.delete()
    }
}

