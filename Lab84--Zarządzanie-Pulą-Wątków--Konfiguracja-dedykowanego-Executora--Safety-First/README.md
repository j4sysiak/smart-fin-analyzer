Lab84
-----

Lab84--Zarządzanie-Pulą-Wątków--Konfiguracja-dedykowanego-Executora--Safety-First
=================================================================================

Wchodzimy w jeden z najważniejszych obszarów dla inżyniera systemów rozproszonych. 
Jako Mid, musisz przestać myśleć o wątkach jako o "czymś, co po prostu działa w tle", a zacząć myśleć o nich jako o zasobach, 
które mogą się skończyć i zabić Twój serwer.

Obecnie w Twoim projekcie masz dwa światy:
1. `GPars`: Używasz go do "dzielenia i rządzenia" (dzielisz listę transakcji na kawałki i liczysz je równolegle). 
    To jest CPU-intensive.

2. Spring `@Async`: Używasz go do "oddelegowania" (mówisz: "wyślij tego maila, a ja wracam do klienta"). 
   To jest I/O-intensive.

Zaczynamy Etap 4: Zarządzanie Pulą Wątków (Thread Pool Orchestration).

Problem: 
Domyślnie Spring używa `SimpleAsyncTaskExecutor`, który dla każdego zadania tworzy... nowy fizyczny wątek. 
Jeśli wpadnie 10 000 eventów naraz, serwer umrze na błąd OutOfMemory: cannot create new native thread.

Rozwiązanie: 
Stworzymy "Ekipę Wykonawczą" `bulkTaskExecutor` o stałym rozmiarze, z kolejką i "zaworem bezpieczeństwa".

Krok-1. Konfiguracja Puli (AsyncConfig.groovy)
----------------------------------------------
Stwórz lub zaktualizuj klasę: `src/main/groovy/pl/edu/praktyki/config/AsyncConfig.groovy`. 
To tutaj zdefiniujemy "płuca" Twojej aplikacji.

```groovy
package pl.edu.praktyki.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableAsync
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import java.util.concurrent.Executor
import java.util.concurrent.ThreadPoolExecutor

@Configuration
@EnableAsync
class AsyncConfig {

    @Bean(name = "bulkTaskExecutor")
    Executor smartFinExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor()
        
        // 1. Rdzeń: Ile wątków zawsze czeka na pracę?
        executor.corePoolSize = 3
        
        // 2. Kolejka: Jeśli kucharze są zajęci, ile zamówień może czekać w kolejce?
        executor.queueCapacity = 100
        
        // 3. Max: Jeśli kolejka jest pełna, do ilu wątków możemy dobić?
        executor.maxPoolSize = 10
        
        // 4. Prefiks: Żebyś w logach widział dokładnie, kto "zapierdala"
        executor.setThreadNamePrefix("bulkTaskExecutorZapierdala--")

        // 5. Zawór bezpieczeństwa (Backpressure): 
        // Co jeśli kucharze zajęci I kolejka pełna? 
        // CallerRunsPolicy mówi: "Wątek, który to wysłał, musi to sam zrobić". 
        // To naturalnie spowalnia napływ danych!
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy())
        
        executor.initialize()
        return executor
    }
}
```

Krok-2. Podpięcie Executora pod kod
-----------------------------------
Teraz musimy powiedzieć Springowi, żeby używał naszej nowej ekipy, a nie domyślnej.

Zmień w `GlobalStatsProjector.groovy` ( i innych miejscach gdzie masz adnotację: `@Async` ):

```groovy
@Async("bulkTaskExecutor") // <--- Wskazujemy konkretną pulę po nazwie Beana
@EventListener
@Transactional
void projectBatchToGlobalSummary(TransactionBatchProcessedEvent event) {
// ... Twoja logika ...
}
```

Krok-3. Test Spock – "Weryfikacja Tożsamości Wątku"
---------------------------------------------------
Napiszemy test, który udowodni, że praca faktycznie wykonuje się na naszej nowej puli FinWorker-.

`src/test/groovy/pl/edu/praktyki/async/AsyncThreadSpec.groovy`

```groovy
package pl.edu.praktyki.async

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationEventPublisher
import pl.edu.praktyki.BaseIntegrationSpec
import pl.edu.praktyki.event.TransactionBatchProcessedEvent
import spock.lang.Shared
import java.util.concurrent.ConcurrentHashMap
import static org.awaitility.Awaitility.await
import java.util.concurrent.TimeUnit

class AsyncThreadSpec extends BaseIntegrationSpec {

    @Autowired ApplicationEventPublisher eventPublisher
    
    // Mapa do zapisania nazwy wątku, który obsłużył event
    @Shared def threadMap = new ConcurrentHashMap<String, String>()

    def "powinien wykonać zadanie na dedykowanej puli wątków FinWorker"() {
        given: "zdarzenie"
        def event = new TransactionBatchProcessedEvent(userName: "ThreadTester", totalBalance: 10)

        when: "publikujemy event"
        eventPublisher.publishEvent(event)

        then: "czekamy, aż system go obsłuży i sprawdzamy nazwę wątku"
        // Musisz w swoim Listenerze (np. GlobalStatsProjector) dopisać:
        // threadMap.put("lastThread", Thread.currentThread().name)
        
        await().atMost(5, TimeUnit.SECONDS).until {
            // W logach powinieneś widzieć: "FinWorker-1"
            println ">>> Zadanie wykonane przez wątek: ${Thread.currentThread().name}"
            return true
        }
    }
}
```

Dlaczego to jest poziom Mid? (Wiedza na rozmowę)
------------------------------------------------
Rekruter zapyta: "Jakie są parametry `ThreadPoolTaskExecutor` i jak je dobrać?"
Twoja odpowiedź:
1. `corePoolSize`:  Stała ekipa. Dobieramy ją wg liczby rdzeni (np. N+1 dla zadań obliczeniowych).

2. `queueCapacity`: Nasz bufor.  Jeśli zadania wpadają falami, kolejka je "wygładza".

3. `maxPoolSize`:   Limit bezpieczeństwa, żeby nie spalić procesora.

4. `RejectedExecutionHandler`: Sposób radzenia sobie z przeciążeniem. 
   `CallerRunsPolicy` to najlepszy sposób na Backpressure – spowalnia klienta, zamiast wywalać błąd.

Zadanie dla Ciebie:
-------------------
Wdroż AsyncConfig.
Oznacz swoje metody `@Async` nazwą "bulkTaskExecutor": `@Async("bulkTaskExecutor")`
Dodaj do logów w klasie `GlobalStatsProjector` informację o wątku: `log.info("Wątek: {}", Thread.currentThread().name)`.
Odpal aplikację i sprawdź, czy w logach zamiast task-1 widzisz FinWorker-1.
Daj znać, czy Twoja nowa ekipa wątków zameldowała się do pracy! 
Kolejny krok to Lab 86: Obsługa błędów w asynchroniczności `AsyncExceptionHandler` 
    – bo jeśli wątek w tle wybuchnie, nikt o tym nie usłyszy, jeśli nie zrobisz tego poprawnie!