To jest moment, w którym nasz projekt zaczyna przypominać prawdziwy system klasy Enterprise. 
Mamy "mięśnie" (wielowątkowy importer GPars) oraz "mózg" (dynamiczny silnik reguł).

W Kroku 4 połączymy je w jeden Pipeline (Rurociąg) Przetwarzania Danych. 
Zrobimy to w taki sposób, aby każda wczytywana w tle transakcja od razu przechodziła przez skanowanie regułami.

Krok 4: Integracja – Rurociąg "Ingest & Analyze"
------------------------------------------------

4.1. Zmiana w TransactionIngesterService.groovy

Wstrzykniemy nasz silnik reguł (TransactionRuleService) do importera i dodamy nową metodę ingestAndApplyRules. Dzięki GPars aplikowanie reguł dla tysięcy transakcji będzie rozproszone na wszystkie rdzenie procesora!

```groovy
package pl.edu.praktyki.service

import org.springframework.stereotype.Service
import org.springframework.beans.factory.annotation.Autowired // DODANE
import pl.edu.praktyki.domain.Transaction
import groovyx.gpars.GParsPool

@Service
class TransactionIngesterService {

    // Wstrzykujemy nasz silnik reguł
    @Autowired
    TransactionRuleService ruleService

    // ... (poprzednie metody zostają) ...

    /**
     * KROK 4: Kompletny rurociąg.
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
```

4.2. Prawdziwy Test Integracyjny (SmartFinIntegrationSpec.groovy)

To jest test, który udowadnia, że cały mechanizm działa. Aby Spock podniósł obie klasy w Springu, musimy wymienić je obie w adnotacji @ContextConfiguration.

Stwórz nowy plik src/test/groovy/pl/edu/praktyki/service/SmartFinIntegrationSpec.groovy:

```groovy
package pl.edu.praktyki.service

import spock.lang.Specification
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.ContextConfiguration
import pl.edu.praktyki.domain.Transaction
import java.time.LocalDate

// Ładujemy OBA serwisy do kontekstu Springa
@ContextConfiguration(classes = [TransactionIngesterService, TransactionRuleService])
class SmartFinIntegrationSpec extends Specification {

    @Autowired
    TransactionIngesterService pipelineService

    def "powinien zaimportować transakcje wielowątkowo i natychmiast oznaczyć je dynamicznymi tagami"() {
        given: "dwie paczki transakcji (np. z dwóch różnych banków)"
        def bankA = [
            new Transaction(id: "A1", date: LocalDate.now(), amount: 5000.0, category: "Praca", description: "Wypłata"),
            new Transaction(id: "A2", date: LocalDate.now(), amount: -15.0, category: "Jedzenie", description: "Kawa")
        ]
        
        def bankB = [
            new Transaction(id: "B1", date: LocalDate.now(), amount: -2500.0, category: "Dom", description: "Czynsz"),
            new Transaction(id: "B2", date: LocalDate.now(), amount: -45.0, category: "Rozrywka", description: "Netflix")
        ]

        and: "zestaw reguł biznesowych zdefiniowanych przez użytkownika"
        def myRules = [
            "if (amount > 0) addTag('INCOME')",
            "if (amount < -1000) addTag('HIGH_EXPENSE')",
            "if (description.contains('Netflix')) addTag('SUBSCRIPTION')"
        ]

        when: "uruchamiamy główny rurociąg przetwarzający dane równolegle"
        // Przekazujemy listę list (bankA, bankB) oraz nasze reguły
        def processedData = pipelineService.ingestAndApplyRules([bankA, bankB], myRules)

        then: "mamy wszystkie 4 transakcje w jednej płaskiej liście"
        processedData.size() == 4

        and: "Wypłata została rozpoznana jako przychód"
        def incomeTx = processedData.find { it.id == "A1" }
        incomeTx.tags.contains("INCOME")

        and: "Czynsz został oznaczony jako wysoki wydatek"
        def rentTx = processedData.find { it.id == "B1" }
        rentTx.tags.contains("HIGH_EXPENSE")

        and: "Netflix został rozpoznany jako subskrypcja"
        def netflixTx = processedData.find { it.id == "B2" }
        netflixTx.tags.contains("SUBSCRIPTION")
        
        and: "Kawa nie dostała żadnego tagu (żadna reguła nie pasuje)"
        def coffeeTx = processedData.find { it.id == "A2" }
        coffeeTx.tags.isEmpty()
    }
}
```
Dlaczego ta architektura jest świetna? (Dla Rekrutera)

Wyobraź sobie, że opowiadasz o tym kodzie na rozmowie rekrutacyjnej:

Rozwiązanie problemu wąskiego gardła: 
Aplikowanie setek reguł (GroovyShell) na dziesiątkach tysięcy transakcji może potrwać. 
Dzięki temu, że daliśmy ruleService.applyRules do środka collectParallel w GPars, analiza idzie w pełni wielowątkowo, 
wykorzystując w 100% procesor maszyny.

Spring Dependency Injection: 
TransactionIngesterService nie musi nic wiedzieć o SecureASTCustomizer czy zabezpieczeniach. 
On tylko prosi Springa o wstrzyknięcie narzędzia do reguł. 
To czysty wzorzec Inversion of Control.