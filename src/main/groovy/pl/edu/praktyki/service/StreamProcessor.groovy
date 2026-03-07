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
}