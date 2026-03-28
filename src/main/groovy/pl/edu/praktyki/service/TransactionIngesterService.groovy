package pl.edu.praktyki.service

import groovy.util.logging.Slf4j
import org.springframework.stereotype.Service
import pl.edu.praktyki.domain.Transaction
import groovyx.gpars.GParsPool
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationEventPublisher
import pl.edu.praktyki.event.TransactionImportedBatchEvent
import pl.edu.praktyki.monitoring.FinanceMetrics

@Service
@Slf4j
class TransactionIngesterService {

    // Wstrzykujemy nasz silnik reguł
    @Autowired
    TransactionRuleService ruleService

    // Wstrzykujemy nasz serwis metryk
    @Autowired
    FinanceMetrics metrics


    /*
    Publikacja zdarzeń (tu: TransactionImportedEvent) pozwala innym komponentom dowiedzieć się
    o zaimportowanej transakcji bez bezpośredniego powiązania z tym serwisem.
    Korzyści:
    ---------
    1. Rozdzielenie odpowiedzialności — nadawca (ingester) nie musi znać odbiorców.
    2. Reakcje per‑transakcja — zapisywanie do DB, aktualizacja UI, wysyłka powiadomień, dalsze przetwarzanie.
    3. Łatwe rozszerzanie — dodanie nowego zachowania = nowy listener, bez zmiany kodu źródłowego.
    4. Skalowalność/asynchroniczność — można obsługiwać zdarzenia asynchronicznie
       lub przepchnąć je do kolejki (Kafka/Rabbit) jeśli potrzeba.
    5. Monitorowanie i audit — listeners mogą zbierać metryki, logi, audyty.

    Wartość praktyczna:
    ------------------
    daje elastyczny, modułowy pipeline.
    Uwaga:
    Springowe zdarzenia są domyślnie synchroniczne — jeśli chcesz uniknąć blokowania wątków GPars,
    skonfiguruj asynchroniczne przetwarzanie zdarzeń i upewnij się, że listenerzy są bezpieczni dla wątków.
    * */
    // Wbudowany w Springa mechanizm wysyłania zdarzeń
    @Autowired ApplicationEventPublisher eventPublisher

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
                log.info('[GPars] Przetwarzam paczkę danych ({} szt.) w wątku: {}', source.size(), Thread.currentThread().name)

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
     * Pobiera paczki danych, wielowątkowo (dzięki GParsPool.withPool(...) ) analizuje je pod kątem reguł i łączy w całość.

    Opis co robi ta metoda:
    -------------------------------
    Metoda ta przyjmuje List<List<Transaction>> allSources i listę nazw reguł rules.
    Jeśli allSources jest puste — zwraca pustą listę.
    potem:
    Uruchamia wielowątkowe przetwarzanie przez GParsPool.withPool i collectParallel
                                   — każda wewnętrzna lista (paczka) jest przetwarzana równolegle.
    Dla każdej transakcji w paczce:
    -------------------------------
     - wywołuje ruleService.applyRules(tx, rules) (aplikuje reguły)
     - publikuje zdarzenie TransactionImportedEvent przez eventPublisher
       (metod musi zawierać logikę wysyłania eventów przez eventPublisher (żeby TransactionAuditListener działał))
     - aktualizuje metryki przez metrics.recordTransaction(tx.amountPLN)
    return:
    -------
    Zwraca jedną, spłaszczoną listę wszystkich transakcji (flatten()), rzutowaną na List<Transaction>.
    */
    List<Transaction> ingestAndApplyRules(List<List<Transaction>> allSources, List<String> rules) {
        if (!allSources) return[]

        // Precompile rules once per pipeline run (costly parsing avoided)
        def compiled = ruleService.compileRules(rules)

        // Jeśli mamy tylko JEDNO źródło, unikamy kosztu inicjalizacji GParsPool
        if (allSources.size() == 1) {
            def source = allSources[0]
            log.info('Analizuję pojedyncze źródło ({} szt.) bez GPars, wątek: {}', source.size(), Thread.currentThread().name)
            // Zastosuj reguły używając wcześniej skompilowanych closure (szybsze niż analiza tekstowa i parallelStream)
            if (compiled && compiled.size() > 0) {
                source.each { tx -> ruleService.applyCompiledRules(tx, compiled) }
            } else {
                ruleService.applyRulesToList(source, rules)
            }
            metrics.recordTransactions(source.size())
            eventPublisher.publishEvent(new TransactionImportedBatchEvent(transactions: source))
            return source as List<Transaction>
        }

        // W przypadku wielu źródeł używamy GPars do rozdzielenia pracy na wątki
        return GParsPool.withPool {
            def parallelResults = allSources.collectParallel { List<Transaction> source ->
                log.info('Analizuję paczkę ({} szt.) w wątku: {}', source.size(), Thread.currentThread().name)

                // Apply rules in a vectorized manner to whole source
                ruleService.applyRulesToList(source, rules)
                metrics.recordTransactions(source.size())
                eventPublisher.publishEvent(new TransactionImportedBatchEvent(transactions: source))
                return source
            }
            return parallelResults.flatten()
        } as List<Transaction>
    }
}