Lab68
-----

Lab68--Asynchroniczne-przetwarzanie-Batchowe--Background-Jobs
-------------------------------------------------------------

Skoro Twój projekt potrafi już błyskawicznie zapisywać dane i bezpiecznie je odczytywać, to z perspektywy Etapu 3 jesteś w 95% gotowy.
Jednak jako Mid-level Developer, musisz wiedzieć, że samo "szybkie zapisywanie" to połowa sukcesu. 
Prawdziwym wyzwaniem jest to, że kiedy proces zapisu trwa (nawet te 2 sekundy dla miliona rekordów), nie chcesz blokować użytkownika. Jeśli użytkownik wyśle milion rekordów przez REST API, a Twój kontroler będzie czekał na zakończenie zapisu, połączenie HTTP może wygasnąć (timeout).

Zanim przejdziemy do pełnego Etapu 4, zrobimy "most" łączący optymalizację danych z asynchronicznością.

Cel: 
Zmiana architektury tak, aby po otrzymaniu wielkiej paczki danych przez API, serwer odpowiedział natychmiast: 
"Otrzymałem, przetwarzam w tle" (Status 202 Accepted), zamiast kazać użytkownikowi czekać.

Krok 1: Konfiguracja Puli Wątków (AsyncConfig.groovy)
-----------------------------------------------------
Mid-developer nigdy nie używa domyślnego mechanizmu asynchronicznego Springa bez konfiguracji, 
bo ten domyślnie tworzy nieskończoną liczbę wątków, co może zabić serwer. 
Stworzymy dedykowaną, nazwaną pulę wątków dla zadań typu "Bulk".

Stwórz plik `src/main/groovy/pl/edu/praktyki/config/AsyncConfig.groovy`:

```groovy
package pl.edu.praktyki.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableAsync
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor

import java.util.concurrent.Executor

@Configuration
@EnableAsync // Włącza obsługę adnotacji @Async
class AsyncConfig {

    @Bean(name = "bulkTaskExecutor")
    Executor bulkTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor()
        executor.corePoolSize = 2       // Ile wątków zawsze czeka
        executor.maxPoolSize = 5        // Maksymalnie ile wątków może powstać
        executor.queueCapacity = 500    // Ile zadań może czekać w kolejce
        executor.setThreadNamePrefix("BulkExecutor-")
        executor.initialize()
        return executor
    }
}
```

Krok 2: Asynchroniczny Serwis Fasady (SmartFinFacade.groovy)
------------------------------------------------------------
Wykorzystamy adnotację `@Async`, aby oddelegować ciężką pracę Twojego `TransactionBulkSaver` do osobnego wątku.

Zaktualizuj (lub stwórz nową metodę) w `pl.edu.praktyki.facade.SmartFinFacade`:

```groovy
import org.springframework.scheduling.annotation.Async
// ... reszta importów

    /**
     * NOWOŚĆ: Asynchroniczne procesowanie. 
     * Metoda kończy się natychmiast, a praca leci w tle na wątku z puli 'bulkTaskExecutor'.
     */
    @Async("bulkTaskExecutor")
    void processInBackgroundTask(String userName, List<Transaction> rawTransactions, List<String> rules) {
        log.info(">>> [ASYNC] Rozpoczynam ciężką pracę w tle dla: {}", userName)
        
        // Wywołujemy Twoją potężną logikę zapisu
        // (Tu wywołaj logikę, którą miałeś w Facade - przeliczanie, reguły i na końcu Twój BulkSaver)
        processAndGenerateReport(userName, rawTransactions, rules)
        
        log.info(">>> [ASYNC] Praca w tle zakończona pomyślnie.")
    }
```

Krok 3: Nowy Endpoint w Kontrolerze (TransactionController.groovy)
------------------------------------------------------------------
Dodamy dedykowany endpoint dla wielkich paczek danych.

```groovy
@PostMapping("/bulk")
@ResponseStatus(HttpStatus.ACCEPTED) // Zwraca kod 202 - "Przyjąłem, zrobię później"
void addBulkTransactions(@RequestBody List<Transaction> transactions) {
log.info(">>> Otrzymano paczkę {} transakcji do przetworzenia asynchronicznego", transactions.size())

        // Nie czekamy na wynik! Odpalamy i zapominamy (Fire and forget)
        facade.processInBackgroundTask("SystemUser", transactions, [])
}
```

Krok 4: Test Spock z Awaitility (AsyncBulkSpec.groovy)
------------------------------------------------------
Jak przetestować coś, co dzieje się w tle? 
Musimy użyć Awaitility (którego uczyliśmy się wcześniej), aby poczekać, aż dane "wpadną" do bazy.

Utwórz klasę testową: `C:\dev\smart-fin-analyzer\src\test\groovy\pl\edu\praktyki\web\AsyncBulkSpec.groovy`
```groovy
package pl.edu.praktyki.web

import pl.edu.praktyki.BaseIntegrationSpec
import pl.edu.praktyki.domain.Transaction
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import static org.awaitility.Awaitility.await
import java.util.concurrent.TimeUnit

@AutoConfigureMockMvc
class AsyncBulkSpec extends BaseIntegrationSpec {

    @Autowired MockMvc mvc
    @Autowired pl.edu.praktyki.repository.TransactionRepository repo

    def "powinien przyjąć wielką paczkę danych i przetworzyć ją w tle"() {
        given: "1000 transakcji"
        def data = (1..1000).collect { new Transaction(id: "ASYNC-$it", amount: 10.0, category: "Async") }
        String json = groovy.json.JsonOutput.toJson(data)

        when: "uderzamy w endpoint /bulk"
        def response = mvc.perform(post("/api/transactions/bulk")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))

        then: "serwer odpowiada 202 Accepted natychmiast"
        response.andExpect(status().isAccepted())
        
        and: "w tej milisekundzie baza wciąż może być pusta"
        repo.count() < 1000

        then: "po krótkiej chwili Awaitility potwierdza, że dane wpadły do bazy"
        await().atMost(5, TimeUnit.SECONDS).until {
            repo.count() == 1000
        }
    }
}
```

Dlaczego to zamyka Etap 3 i otwiera Etap 4?

Odporność na Timeouty: 
Twoje API nigdy się nie "zawiesi" przy wielkim imporcie.

Zarządzanie zasobami: 
Dzięki maxPoolSize = 5 masz gwarancję, że nawet jeśli 100 użytkowników wyśle dane naraz, 
serwer nie odpali 100 wątków (co zabiłoby procesor), tylko grzecznie ustawi zadania w kolejce.

User Experience: 
Klient dostaje odpowiedź w 50ms, a ciężka praca dzieje się tam, gdzie jej miejsce – na zapleczu.

Wdróż te zmiany! Jeśli to zadziała, będziemy gotowi na Etap 4: Spring Events, 
gdzie zamiast wołać metody bezpośrednio, zaczniemy rozsyłać komunikaty po całym systemie.

 


