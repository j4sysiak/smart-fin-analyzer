package pl.edu.praktyki.parser

import spock.lang.Specification
import java.nio.file.Files

class CsvTransactionParserSpec extends Specification {

    def "powinien sparsować plik CSV na listę transakcji"() {
        given:
        def csvContent = """\
id,amount,currency,category,description,date
1,100,EUR,Jedzenie,Obiad,2025-07-22
2,-50,USD,Rozrywka,Kino,2025-07-22
3,2000,PLN,Praca,Bonus,2025-07-22"""

        def tempFile = Files.createTempFile("test", ".csv").toFile()
        tempFile.text = csvContent
        tempFile.deleteOnExit()

        def parser = new CsvTransactionParser()

        when:
        def result = parser.parse(tempFile)

        then:
        result.size() == 3
        result[0].id == "1"
        result[0].amount == 100
        result[0].currency == "EUR"
        result[0].category == "Jedzenie"
        result[1].amount == -50
        result[2].currency == "PLN"
    }
}