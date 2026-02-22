Lab 27
------

Chcemy, żeby po przetworzeniu każdej transakcji system "wykrzyczał" w tle informację o tym.

Krok 1: Stwórz obiekt Zdarzenia
-------------------------------

Stwórz nowy plik `src/main/groovy/pl/edu/praktyki/event/TransactionImportedEvent.groovy`:

```groovy
package pl.edu.praktyki.event

import pl.edu.praktyki.domain.Transaction

class TransactionImportedEvent {
    Transaction transaction
}
```

Krok 2: Stwórz Słuchacza (Listenera)
-------------------------------------

Stwórz plik `src/main/groovy/pl/edu/praktyki/service/TransactionAuditListener.groovy`. 
Ten serwis będzie cichutko nasłuchiwał w tle.

```groovy
package pl.edu.praktyki.service

import org.springframework.stereotype.Service
import org.springframework.context.event.EventListener
import pl.edu.praktyki.event.TransactionImportedEvent

@Service
class TransactionAuditListener {

    @EventListener
    void onNewTransaction(TransactionImportedEvent event) {
        // Ta metoda uruchomi się SAMA, gdy ktoś wyśle zdarzenie!
        println ">>> [ZDARZENIE W TLE] System zauważył nową transakcję: ${event.transaction.id}"
    }
}
```


Krok 3: Wyślij zdarzenie z Ingestera
------------------------------------

Otwórz `src/main/groovy/pl/edu/praktyki/service/TransactionIngesterService.groovy`. 
Dodaj wstrzyknięcie publikatora i wywołaj go.

```groovy
package pl.edu.praktyki.service

import org.springframework.stereotype.Service
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationEventPublisher // <-- Import
import pl.edu.praktyki.domain.Transaction
import pl.edu.praktyki.event.TransactionImportedEvent // <-- Import
import groovyx.gpars.GParsPool

@Service
class TransactionIngesterService {

    @Autowired TransactionRuleService ruleService
    
    // DODAJ TO: Wbudowany w Springa mechanizm wysyłania zdarzeń
    @Autowired ApplicationEventPublisher eventPublisher 

    // ... stara metoda ingestTransactions zostaje bez zmian ...

    List<Transaction> ingestAndApplyRules(List<List<Transaction>> allSources, List<String> rules) {
        if (!allSources) return []

        return GParsPool.withPool {
            def parallelResults = allSources.collectParallel { List<Transaction> source ->
                source.each { tx ->
                    ruleService.applyRules(tx, rules)
                    
                    // DODAJ TO: Wysyłamy zdarzenie w eter
                    eventPublisher.publishEvent(new TransactionImportedEvent(transaction: tx))
                }
                return source
            }
            return parallelResults.flatten()
        } as List<Transaction>
    }
}
```

Dlaczego to nie zepsuje testów?
Zmieniliśmy zachowanie "w tle", ale nie zmieniliśmy tego, co metoda zwraca.
Test `SmartFinIntegrationSpec` nadal otrzyma listę 4 transakcji, więc asercje (w sekcji then:) przejdą gładko.
ApplicationEventPublisher to standardowy element Springa. Podczas uruchamiania testu (@ContextConfiguration), Spring sam automatycznie wstrzyknie ten obiekt, więc nie dostaniesz błędu NullPointerException.


