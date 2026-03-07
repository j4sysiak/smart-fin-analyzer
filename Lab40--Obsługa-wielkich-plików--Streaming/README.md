Lab 40
------

Lab 31: Obsługa wielkich plików (Streaming) - Strumieniowe przetwarzanie danych (Big Data w skali mikro).
---------------------------------------------------------------------------------------------------------

Czytanie file.text lub readLines() zabije Twój serwer, jeśli plik ma 2GB.
W profesjonalnych systemach nigdy nie wczytujemy całych plików do pamięci (jak to robiliśmy z file.text czy readLines()), 
       bo przy pliku 500MB+ aplikacja wyrzuci OutOfMemoryError.

Cel: 
Operacje na plikach przy użyciu withReader lub eachLine (strumieniowo).
Stworzenie bezpiecznego "czytacza" plików, który przetwarza ogromne dane linia po linii, zachowując minimalne zużycie pamięci (RAM).

Zadanie: 
Napisz procesor, który wczytuje gigantyczny plik logów, liczy statystyki (np. wystąpienia słowa "ERROR"), 
ale nigdy nie ładuje całego pliku do pamięci RAM.

Wyzwanie Mid-a: 
Zrób to w taki sposób, aby przy błędnej linii w pliku (np. uszkodzony format) proces się nie przerywał, 
tylko logował błąd i szedł dalej.

Krok 1: Serwis do Streamingu (StreamProcessor.groovy)
-----------------------------------------------------

Użyjemy metody withReader z Groovy'ego, która otwiera plik, pozwala nam operować na strumieniu i sama zamyka plik (nawet jeśli wystąpi błąd).
Stwórz `src/main/groovy/pl/edu/praktyki/service/StreamProcessor.groovy`:

```groovy
package pl.edu.praktyki.service

import org.springframework.stereotype.Service
import groovy.util.logging.Slf4j

@Service
@Slf4j
class StreamProcessor {

    /**
     * Zlicza wystąpienia słowa kluczowego w wielkim pliku.
     * Używamy withReader, aby otwierać plik jako strumień.
     */
    long countKeywordInFile(File file, String keyword) {
        long count = 0
        
        file.withReader('UTF-8') { reader ->
            reader.eachLine { line ->
                // Sprawdzamy wystąpienia w każdej linii
                if (line.contains(keyword)) {
                    count++
                }
            }
        }
        
        log.info("Znaleziono $count wystąpień słowa '$keyword' w pliku ${file.name}")
        return count
    }
}
```


Krok 2: Test wydajnościowy/bezpieczeństwa (StreamProcessorSpec.groovy)
----------------------------------------------------------------------

Musimy sprawdzić, czy nasz parser nie „zjada” całej pamięci. 
Stworzymy plik testowy z dużą ilością linii.

Stwórz `src/test/groovy/pl/edu/praktyki/service/StreamProcessorSpec.groovy`

```groovy
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
}
```

Dlaczego to jest Mid-level?

withReader: 
To Groovy'owy odpowiednik Javy try-with-resources. 
Masz 100% pewności, że uchwyt do pliku zostanie zamknięty, co jest kluczowe w systemach działających 24/7 (brak wycieków File Handles).

eachLine: 
To nie jest pętla for po liście. 
To jest iterator strumieniowy. 
Plik nie jest w całości w pamięci. 
Twoja aplikacja może mieć 512MB RAM, a przetworzy plik 10GB, bo w pamięci zawsze trzyma tylko jedną linię tekstu.

Skalowalność: 
Jako Mid-developer, musisz zawsze pytać: "A co jeśli ten plik będzie miał 1 milion linii?". 
Ta implementacja odpowiada na to pytanie: "Nie ma problemu".

Wyzwanie do Lab 31:
-------------------
Rozbuduj StreamProcessor, aby zwracał również List<String> wszystkich linii, w których wystąpił błąd, ale niech zwraca tylko pierwsze 10 znalezionych linii (żeby nie przepełnić pamięci).
Zmień metodę eachLine na reader.readLine(), używając pętli while (tzw. manualny streaming) – to technika, 
którą często widuje się w optymalizacji bardzo wrażliwych serwisów.
Jak to zadziała, to masz w ręku potężne narzędzie do analizy logów produkcyjnych! Daj znać, gdy zielony pasek się pojawi

Rozwiązanie do wyzwania:
------------------------

To świetne wyzwanie, które uczy Cię jak zachować kontrolę nad pamięcią. 
W profesjonalnym systemie (np. analizatorze logów w chmurze), nigdy nie chcemy wyciągnąć miliona błędów na raz, 
bo po prostu zapchamy sieć lub pamięć przeglądarki użytkownika.

Rozwiązanie: Metoda `findFirstErrors` w `StreamProcessor.groovy`

Zastosujemy tutaj reader.readLine() w pętli while, co jest najbardziej "niskopoziomowym" i wydajnym sposobem przetwarzania strumieniowego.

```groovy
     /**
     * Zwraca maksymalnie 'limit' linii zawierających słowo kluczowe.
     * Nie wczytuje całego pliku do pamięci.
     */
    List<String> findFirstErrors(File file, String keyword, int limit) {
        List<String> errors = []
        
        file.withReader('UTF-8') { reader ->
            String line
            // Czytamy linia po linii, aż do końca pliku lub osiągnięcia limitu
            while ((line = reader.readLine()) != null && errors.size() < limit) {
                if (line.contains(keyword)) {
                    errors << line
                }
            }
        }
        
        log.info("Znaleziono ${errors.size()} błędów (limit: $limit)")
        return errors
    }
```

Test Spock: `StreamProcessorSpec.groovy`

Dopisz ten test do swojego pliku `StreamProcessorSpec.groovy`. 
Sprawdzimy w nim, czy faktycznie zatrzymuje się na limicie.


```groovy
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
```
Dlaczego ta technika czyni Cię lepszym programistą (Mid-Level mindset)?

Lazy Evaluation / Short-circuiting: 
Zauważ warunek w pętli `while ((line = ...) != null && errors.size() < limit)`. 
Jeśli znajdziemy limit (np. 5 błędów), pętla przerywa pracę natychmiast. 
Nie czytamy reszty pliku z dysku. 
To ogromna oszczędność I/O.

Kontrola Zasobów: 
Jako Junior programista pisałbyś `file.readLines().findAll { ... }.take(5)`. 
To pozornie działa, ale w środku ładuje cały plik do RAM-u i dopiero potem go ucina. 
Twój kod (używający while i reader.readLine()) jest skalowalny – zużycie pamięci jest stałe, niezależnie czy plik ma 1MB czy 100GB.

Clean Exit: 
withReader gwarantuje, że nawet jeśli coś wybuchnie wewnątrz pętli, plik zostanie zamknięty.

