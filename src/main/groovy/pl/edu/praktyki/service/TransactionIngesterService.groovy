package pl.edu.praktyki.service

import org.springframework.stereotype.Service
import pl.edu.praktyki.domain.Transaction
import groovyx.gpars.GParsPool
import org.springframework.beans.factory.annotation.Autowired // DODANE

@Service
class TransactionIngesterService {

    // Wstrzykujemy nasz silnik reguł
    @Autowired
    TransactionRuleService ruleService

    /**
     * Docelowo ta metoda będzie przyjmować listę ścieżek do plików
     * i przetwarzać je równolegle.
     */
    List<Transaction> ingestTransactions(List<Transaction> rawData) {
        // Na razie tylko zwracamy dane - logikę GPars dodamy w następnym kroku
        return rawData
    }

    /**
     * KROK-3: Przyjmuje listę list transakcji.
     * Przetwarza każdą wewnętrzną listę w osobnym wątku i zwraca jedną, płaską listę transakcji.
     */
    List<Transaction> ingestFromMultipleSources(List<List<Transaction>> allSources) {
        // Sprawdzenie krawędziowe
        if (!allSources) return []

        // withPool() inicjalizuje silnik wielowątkowy GPars
        GParsPool.withPool {
            // collectParallel sprawia, że każda paczka (source)
            // jest przetwarzana równolegle na innym rdzeniu procesora
            def parallelResults = allSources.collectParallel { List<Transaction> source ->
                println "[GPars] Przetwarzam paczkę danych (${source.size()} szt.) w wątku: ${Thread.currentThread().name}"

                // Tutaj mogłaby być dodatkowa logika, np. filtrowanie duplikatów wewnątrz paczki
                return source
            }

            // Ponieważ collectParallel zwraca List<List<Transaction>>,
            // używamy flatten(), aby otrzymać jedną, płaską listę transakcji.
            return parallelResults.flatten()
        } as List<Transaction>
    }

    /**
     * KROK 4: Kompletny rurociąg (pipeline).
     * Pobiera paczki danych, wielowątkowo analizuje je pod kątem reguł i łączy w całość.
     */
    List<Transaction> ingestAndApplyRules(List<List<Transaction>> allSources, List<String> rules) {
        if (!allSources) return[]

        return GParsPool.withPool {
            def parallelResults = allSources.collectParallel { List<Transaction> source ->
                println " Analizuję paczkę (${source.size()} szt.) w wątku: ${Thread.currentThread().name}"

                // Dla każdej transakcji w tej paczce wywołujemy silnik reguł
                source.each { tx ->
                    ruleService.applyRules(tx, rules)
                }

                return source
            }

            return parallelResults.flatten()
        } as List<Transaction> // Rzutowanie, o którym pamiętamy!
    }
}