Lab70
-----

Lab70--Asynchroniczność-i-Eventy-Zarządzanie-Pulą-Wątków-i-Monitoring--Thread-Pool-Management
---------------------------------------------------------------------------------------------

Wiesz już, że adnotacje to nie tylko napisy, ale fizyczne obiekty (Proxy), które zmieniają sposób dostępu do danych.
Do tej pory system jest już asynchroniczny i sterowany zdarzeniami. 
Ale jako Mid-level Developer musisz zadać sobie teraz kluczowe pytanie:
"Co się stanie, jeśli w nocy przyjdzie 10 milionów transakcji? Czy moje wątki asynchroniczne nie 'zabiją' serwera, 
zapychając całą pamięć RAM?"

 
Konfiguracja "Safety First" i Monitoring Kolejki
------------------------------------------------

Cel: 
Ustawienie limitów dla Twojej puli wątków, aby aplikacja była stabilna pod obciążeniem, 
oraz wystawienie metryk, żebyś widział w `/actuator`, ile zadań czeka w kolejce.

Krok 1: Tuning AsyncConfig.groovy
---------------------------------
Musimy skonfigurować tzw. `Reject Policy`. 
Jeśli kolejka się zapcha (u nas limit to 500 zadań), nie chcemy, żeby aplikacja po prostu "wybuchła". 
Chcemy, żeby np. wątek główny przejął zadanie (spowolni to system, ale go nie wyłączy).

Zaktualizuj `src/main/groovy/pl/edu/praktyki/config/AsyncConfig.groovy`:

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
    Executor bulkTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor()
        
        // Konfiguracja sprzętowa
        executor.corePoolSize = 2
        executor.maxPoolSize = 4
        executor.queueCapacity = 50 // Mała kolejka, żeby łatwo było przetestować limity
        
        executor.setThreadNamePrefix("BulkAsync-")

        // MAGIA MIDA: Co zrobić, gdy maxPoolSize i queueCapacity są pełne?
        // CallerRunsPolicy: Wątek, który wysłał zadanie (np. HTTP thread), 
        // sam musi je wykonać. To naturalny hamulec dla systemu!
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy())
        
        executor.initialize()
        return executor
    }
}
```

Krok 2: Podglądanie "bebechów" (Actuator + Micrometer)
------------------------------------------------------
Chcemy widzieć w metrykach, ile wątków jest zajętych. 
Spring Boot robi to automatycznie, jeśli poprawnie nazwiemy Beana, ale my dodamy coś ekstra.

Uruchom aplikację:
`  ./gradlew runSmartFinDb -PappArgs="-u Jacek -f transakcje.csv"  `


I gdy aplikacja "wisi" - czyli jest gotowa:
```text
    <==========---> 80% EXECUTING [39s]
    > :runSmartFinDb
```


wejdź pod adres:
👉 http://localhost:8080/actuator/metrics/executor.active

(W parametrze tag wybierz name:bulkTaskExecutor). 
Zobaczysz tam na żywo, ile Twoich wątków asynchronicznych aktualnie pracuje.

 
```json
{
  "name": "executor.active",
  "description": "The approximate number of threads that are actively executing tasks",
  "baseUnit": "threads",
  "measurements": [
    {
      "statistic": "VALUE",
      "value": 0
    }
  ],
  "availableTags": [
    {
      "tag": "name",
      "values": [
        "taskScheduler",
        "bulkTaskExecutor"
      ]
    }
  ]
}
```

Krok 3: Wyzwanie – Test "Przeciążeniowy" (Stress Test)
------------------------------------------------------
Napiszemy test, który wyśle 200 zdarzeń naraz do kolejki, która mieści tylko 50. 
Sprawdzimy, czy system przeżyje.

Stwórz `src/test/groovy/pl/edu/praktyki/event/AsyncStressSpec.groovy`:

```groovy
package pl.edu.praktyki.event

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationEventPublisher
import pl.edu.praktyki.BaseIntegrationSpec
import pl.edu.praktyki.service.AsyncNotificationService
import java.util.concurrent.TimeUnit
import static org.awaitility.Awaitility.await

class AsyncStressSpec extends BaseIntegrationSpec {

    @Autowired ApplicationEventPublisher eventPublisher
    @Autowired AsyncNotificationService notificationService

    def "powinien przetrwać zalanie systemu zdarzeniami (Stress Test)"() {
        given: "resetujemy licznik"
        notificationService.reset()

        when: "bombardujemy system 200 zdarzeniami"
        (1..200).each { i ->
            eventPublisher.publishEvent(new TransactionBatchProcessedEvent(
                userName: "User-$i", totalBalance: 100, generatedReport: "Empty"
            ))
        }

        then: "system nie wybuchł (brak RejectedExecutionException)"
        noExceptionThrown()

        and: "wszystkie 200 zadań zostanie ostatecznie wykonanych (dzięki CallerRunsPolicy)"
        await().atMost(30, TimeUnit.SECONDS).until {
            notificationService.getProcessedCount() == 200
        }
    }
}
```

Dlaczego to zadanie jest kluczowe na Mida?

Backpressure (Nadciśnienie): 
Zrozumiałeś, że zasoby serwera są skończone. 
Ustawiając CallerRunsPolicy, zaimplementowałeś mechanizm, który automatycznie spowalnia przyjmowanie nowych danych, gdy serwer nie wyrabia.

Monitoring:
Mid-developer nie wierzy na słowo, że "działa w tle". 
On patrzy w metryki executor.active i executor.queued.

Stability: 
Twoja aplikacja stała się odporna na tzw. "Spike'i" (nagłe skoki ruchu).

Zadanie: 
Wdróż nową konfigurację i odpal Stress Test. 
Sprawdź metryki executor.active (przybliżona liczba aktywnie wykonujących się wątków) 
oraz executor.queued (liczba zadań oczekujących w kolejce). 
Otwórz:
http://localhost:8080/actuator/metrics/executor.active
http://localhost:8080/actuator/metrics/executor.queued

i wybierz tag name:bulkTaskExecutor.


