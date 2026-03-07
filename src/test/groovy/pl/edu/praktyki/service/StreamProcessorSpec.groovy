package pl.edu.praktyki.service

import spock.lang.Specification

class StreamProcessorSpec extends Specification {

    def "powinien poprawnie policzyć słowa w dużym pliku bez ładowania go do RAM"() {
        given: "tworzymy duży plik tymczasowy (np. 100 000 linii)"
        def tempDir = File.createTempDir()
        def largeFile = new File(tempDir, "huge.log")

        // Zapisujemy plik używając withWriter (też strumieniowo!)
        largeFile.withWriter('UTF-8') { writer ->
            100_000.times { i ->
                writer.writeLine("LOG_ENTRY_$i: Coś się dzieje, szukamy ERROR")
            }
            writer.writeLine("LOG_ENTRY_FINAL: ERROR krytyczny")
        }

        and: "serwis"
        def processor = new StreamProcessor()

        when: "przetwarzamy plik"
        def result = processor.countKeywordInFile(largeFile, "ERROR")

        then: "liczba powinna wynosić 100 001"
        result == 100_001

        cleanup:
        tempDir.deleteDir()
    }

    def "powinien zwrócić tylko pierwszych N znalezionych błędów"() {
        given: "plik z 1000 liniami błędów"
        def tempDir = File.createTempDir()
        def largeFile = new File(tempDir, "errors.log")

        largeFile.withWriter('UTF-8') { writer ->
            1000.times { i ->
                writer.writeLine("LOG_$i: ERROR occurred")
            }
        }

        and: "serwis"
        def processor = new StreamProcessor()

        when: "prosimy tylko o 5 pierwszych błędów"
        def result = processor.findFirstErrors(largeFile, "ERROR", 5)

        then: "otrzymujemy dokładnie 5 elementów"
        result.size() == 5
        result[0] == "LOG_0: ERROR occurred"
        result[4] == "LOG_4: ERROR occurred"

        and: "nie dostaliśmy więcej niż limit"
        !result.find { it.contains("LOG_5") } // LOG_5 nie powinien istnieć w wyniku

        cleanup:
        tempDir.deleteDir()
    }
}