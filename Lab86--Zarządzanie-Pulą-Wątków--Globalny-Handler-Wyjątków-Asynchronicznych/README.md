Lab86
-----

Lab86--Zarządzanie-Pulą-Wątków--Globalny-Handler-Wyjątków-Asynchronicznych
==========================================================================

Problem: 
"Ciche wybuchy" w tle
Kiedy metoda `@Async` zwraca void (tak jak Twoje listenery), a w środku wystąpi błąd (np. baza danych padnie), 
to ten wyjątek nie ma dokąd "wrócić". 
Nie ma go kto złapać (try-catch w Fasadzie nie zadziała, bo Fasada już dawno skończyła robotę). 
Wyjątek po prostu "umiera" w wątku tła, a Ty o tym nie wiesz.

Cel: 
Stworzenie centralnego punktu, który przechwyci każdy błąd z dowolnej metody `@Asyn`c w systemie 
i odnotuje go w logach oraz w naszym `ThreadTrackerze`.

Krok-1. Implementacja Handlera (CustomAsyncExceptionHandler.groovy)
-------------------------------------------------------------------
Stwórz plik `src/main/groovy/pl/edu/praktyki/config/CustomAsyncExceptionHandler.groovy`
Ta klasa implementuje specjalny interfejs Springa.

```groovy
package pl.edu.praktyki.config

import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import pl.edu.praktyki.service.ThreadTracker
import groovy.util.logging.Slf4j
import java.lang.reflect.Method

@Slf4j
@Component
class CustomAsyncExceptionHandler implements AsyncUncaughtExceptionHandler {

    @Autowired
    ThreadTracker threadTracker

    @Override
    void handleUncaughtException(Throwable ex, Method method, Object... params) {
        log.error(">>> [ASYNC-FATAL] Wyjątek w metodzie asynchronicznej: {}", method.name)
        log.error(">>> Komunikat błędu: {}", ex.message)

        // Zapisujemy informację o awarii do naszego trackera, 
        // żeby administrator widział błąd w REST API /monitoring/threads
        threadTracker.put("ERROR.${method.name}", [
            timestamp: System.currentTimeMillis(),
            exception: ex.class.simpleName,
            message: ex.message,
            thread: Thread.currentThread().name
        ])
    }
}
```

Krok-2. Rejestracja w AsyncConfig.groovy
----------------------------------------
Musimy powiedzieć Springowi, żeby używał naszego handlera. 
W tym celu `AsyncConfig` musi implementować interfejs `AsyncConfigurer`.

Zaktualizuj `src/main/groovy/pl/edu/praktyki/config/AsyncConfig.groovy`

```groovy
package pl.edu.praktyki.config

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableAsync
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.Executor
import org.springframework.scheduling.annotation.AsyncConfigurer // DODAJ TO, żeby móc ustawić niestandardowy AsyncUncaughtExceptionHandler
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler

// (konfiguracja Springa poniżej)
// Adnotacje takie jak @Async czy @Transactional tworzą "opakowanie" wokół Twojej klasy.
// Chodzi mi o klasę będącą beanem Springa — czyli klasę zarządzaną przez kontener
// np. oznaczoną @Component, @Service, @Repository, @Configuration albo @Bean.
// Adnotacje takie jak @Async czy @Transactional działają przez utworzenie proxy wokół tego beana
// i przechwytywanie wywołań metod przychodzących z zewnątrz.

// Włącza obsługę adnotacji @Async.
// Dzięki temu możesz oznaczać metody jako asynchroniczne, a Spring będzie je wykonywał w osobnych wątkach.
// "Ekipę Wykonawczą" `bulkTaskExecutor` o stałym rozmiarze, z kolejką i "zaworem bezpieczeństwa"
@EnableAsync
@Configuration
class AsyncConfig implements AsyncConfigurer { // IMPLEMENTUJEMY INTERFEJS AsyncConfigurer, żeby móc ustawić niestandardowy AsyncUncaughtExceptionHandler

    @Autowired
    CustomAsyncExceptionHandler customAsyncExceptionHandler

    // Dzięki tej metodzie sprawiamy, że mój `bulkTaskExecutor` stał się domyślną pulą dla całej aplikacji.
    // Teraz nawet jeśli napiszemy samo @Async (bez nazwy w nawiasie),
    // Spring użyje Twojej zoptymalizowanej puli, a nie swojej domyślnej (często słabej).
    @Override
    Executor getAsyncExecutor() {
        return bulkTaskExecutor()
    }

    // Łączymy nasz handler z mechanizmem Springa
    // Dzięki getAsyncUncaughtExceptionHandler() mamy pewność,
    // że każdy błąd w metodzie void zostanie złapany przez CustomAsyncExceptionHandler.
    @Override
    AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return customAsyncExceptionHandler
    }

    @Bean(name = "bulkTaskExecutor")
    Executor bulkTaskExecutor() {

        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor()

        // Konfiguracja sprzętowa - zwiększona pula dla testów integracyjnych i dużych batchy

        // 1. Rdzeń: Ile wątków zawsze czeka na pracę?
        executor.corePoolSize = 8

        // 2. Max: Jeśli kolejka jest pełna, do ilu wątków możemy dobić?
        executor.maxPoolSize = 16

        // 3. Kolejka: Jeśli kucharze są zajęci, ile zamówień może czekać w kolejce?
        executor.queueCapacity = 2000 // duża kolejka, żeby nie blokować przyjmowania dużych paczek

        // 4. Prefiks: Żebyś w logach widział dokładnie, kto "zapierdala"
        // Podczas analizy logów (tail -f logs/smart-fin.log) albo w ThreadTrackerze (utworzony w Lab85),
        // od razu widzisz: "O, to zadanie wykonuje moja specyficzna pula, a nie jakiś domyślny task-1".
        // To drastycznie ułatwia debugowanie systemów wielowątkowych.
        executor.setThreadNamePrefix("bulkTaskExecutorZapierdala--")



        // MAGIA MIDA: Co zrobić, gdy maxPoolSize i queueCapacity są pełne?
        // CallerRunsPolicy: Wątek, który wysłał zadanie (np. HTTP thread),
        // sam musi je wykonać. To naturalny hamulec dla systemu!

        // Ustawiając CallerRunsPolicy, implementujemy mechanizm, który automatycznie spowalnia przyjmowanie nowych danych,
        // gdy serwer nie wyrabia.

        // To znaczy, że gdy puli wątków i kolejka będą pełne, zadanie nie zostanie odrzucone ani wrzucone do innej kolejki
        // — wątek, który wysłał zadanie (np. wątek obsługi żądania HTTP), sam wykona to zadanie.
        // To działa jako naturalny mechanizm throttlingu: spowalnia przyjmowanie nowych pracy zamiast odrzucać zadania.
        // Plus: unikasz odrzuceń i nagłego wzrostu pamięci;
        // Minus: wątek wywołujący może się blokować i zwiększyć latencję.
        // Linia ustawia tę politykę dla executora.


        // 5. Zawór bezpieczeństwa (Backpressure):
        // Co jeśli kucharze zajęci I kolejka pełna?
        // CallerRunsPolicy mówi: "Wątek, który to wysłał, musi to sam zrobić".
        // To naturalnie spowalnia napływ danych!
        // przykład:
        // Jeśli aplikacja zostanie zalana milionem transakcji,
        // a 16 wątków i 2000 miejsc w kolejce się zapełni,
        // to wątek, który chciał dodać kolejne zadanie (np. wątek obsługujący HTTP), sam je wykona.
        // Efekt: System nie wybuchnie, tylko naturalnie spowolni przyjmowanie nowych danych, dając bazie danych czas na oddech.
        // To jest profesjonalny Backpressure mechanizm.
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy())


        executor.initialize()
        return executor
    }
}
```

Krok-3. Test Spock – "The Silent Explosion" (AsyncErrorSpec.groovy)
-------------------------------------------------------------------
Stworzymy test, który celowo zepsuje zadanie w tle i sprawdzi, czy błąd został zarejestrowany.

Stwórz `src/test/groovy/pl/edu/praktyki/async/AsyncErrorSpec.groovy`

```groovy
package pl.edu.praktyki.async

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import org.springframework.scheduling.annotation.Async
import pl.edu.praktyki.BaseIntegrationSpec
import pl.edu.praktyki.service.ThreadTracker
import spock.lang.Specification
import static org.awaitility.Awaitility.await
import java.util.concurrent.TimeUnit

// Mały serwis pomocniczy tylko do testu błędu
@Service
class EvilService {
@Async("bulkTaskExecutor")
void throwErrorAsync() {
throw new RuntimeException("KATASTROFA W TLE")
}
}

class AsyncErrorSpec extends BaseIntegrationSpec {

    @Autowired EvilService evilService
    @Autowired ThreadTracker threadTracker

    def "powinien przechwycić i zarejestrować wyjątek rzucony asynchronicznie"() {
        when: "odpalamy zadanie, które na 100% wybuchnie"
        evilService.throwErrorAsync()

        then: "Czekamy asynchronicznie, aż Globalny Handler złapie błąd i wpisze do trackera"
        await().atMost(5, TimeUnit.SECONDS).until {
            threadTracker.get("ERROR.throwErrorAsync") != null
        }

        and: "dane błędu w trackerze są poprawne"
        Map errorInfo = threadTracker.get("ERROR.throwErrorAsync") as Map
        errorInfo.message == "KATASTROFA W TLE"
        errorInfo.exception == "RuntimeException"
        
        println ">>> SUKCES: Wyjątek w tle został poprawnie przechwycony przez CustomAsyncExceptionHandler!"
    }
}
```

Dlaczego to jest "Mid-level Hardcore"?

Observability (Obserwowalność): 
Większość systemów ma "puste dziury" w logach. 
Ty sprawiłeś, że błędy asynchroniczne są widoczne w Twoim systemie monitoringu (MonitoringController).

Inversion of Control: 
Nie musisz dodawać try-catch do każdego listenera. 
Masz jeden "centralny mózg" od błędów.

Refleksja: 
Użyliśmy `java.lang.reflect.Method`, aby dynamicznie dowiedzieć się, która metoda wybuchła.

Zadanie dla Ciebie:
-------------------
1. Wdroż handlera `CustomAsyncExceptionHandler` i zaktualizuj `AsyncConfig`.
2. Dodaj test `AsyncErrorSpec` i zobacz, jak Awaitility potwierdza przechwycenie błędu.
3. Wywołaj swój endpoint `/api/admin/monitoring/threads` (w Postmanie lub przeglądarce) 
   po uruchomieniu testu błędu – powinieneś zobaczyć tam informację o "KATASTROFIE W TLE".

Daj znać, czy udało Ci się "usłyszeć" cichy wybuch w tle!  



////////////////   jak to przetestować:  tj: /api/admin/monitoring/threads

To jest świetne pytanie, bo dotyka bardzo ważnego rozróżnienia: 
czym różni się uruchomienie testu od uruchomienia samej aplikacji.

Wyjaśnię Ci to na dwa sposoby: najpierw teorię "dlaczego to robimy", a potem instrukcję "jak to wyklikać".

1. O co w tym chodzi? (Idea)

Wyobraź sobie, że Twoja aplikacja to czarna skrzynka lecąca samolotem.
Logi (log.error) są jak czarna skrzynka – zapisują wszystko na dysk. 
Jeśli coś wybuchnie, mechanik musi iść, wyciągnąć plik i go przeczytać.

Monitoring REST (/api/admin/monitoring/threads) to pulpit pilota. 
Pilot (administrator) może w każdej chwili zapytać: "Hej, czy wszystko ok?".

Dzięki temu, co zrobiliśmy w Labie 86, Twój Globalny Handler Błędów nie tylko pisze do logów, 
ale też "wpisuje na pulpit" informację: "Metoda X wybuchła błędem Y". Dzięki temu admin widzi awarię w przeglądarce, zanim jeszcze zajrzy do plików logów.

2. Dlaczego NIE widzisz tego po uruchomieniu testu? (Pułapka)

Kiedy uruchamiasz test `AsyncErrorSpec` przez `./gradlew clean test`, dzieje się to w osobnym procesie, 
który po 5 sekundach znika.
Jeśli w tym samym czasie masz włączoną aplikację przez `./gradlew runSmartFinDb`, to są to dwa różne światy. 
Aplikacja w przeglądarce nie wie, co wybuchło w Twoim teście.

3. Jak to zobaczyć "na żywo"? (Instrukcja krok po kroku)

Aby zobaczyć błąd w przeglądarce/Postmanie, musisz "wywołać katastrofę" w działającej aplikacji, a nie w teście.

Krok A: Dodaj "Zły guzik" do aplikacji
--------------------------------------
Aby móc to przetestować bez pisania testów, dodajmy na chwilę endpoint w `MonitoringController.groovy`, 
który celowo psuje system:

```groovy
// Dodaj to do MonitoringController.groovy
@Autowired EvilService evilService // Wstrzyknij zły serwis

@GetMapping("/trigger-error")
@PreAuthorize("hasRole('ADMIN')")
String triggerError() {
evilService.throwErrorAsync() // To wywoła asynchroniczny wybuch
return "Zlecono wybuch w tle. Sprawdź /threads za chwilę."
}
```

Krok B: Procedura testowa
-------------------------
1. Uruchom aplikację: `./gradlew runSmartFinDb -PappArgs="-u Jacek -f transakcje.csv"`
2. Zaloguj się w Postmanie jako Admin (pobierz token JWT).
3. Wywołaj błąd: Wyślij GET do http://localhost:8080/api/admin/monitoring/trigger-error (z tokenem).
   
   response powinien być: "Zlecono wybuch w tle. Sprawdź /threads za chwilę."

4. Poczekaj sekundę (żeby wątek w tle zdążył wybuchnąć i handler zdążył to zapisać).
5. Sprawdź pulpit: Wyślij GET do http://localhost:8080/api/admin/monitoring/threads.
6. Co powinieneś zobaczyć w JSON-ie?

W sekcji activeTasksInfo obok normalnych logów o wątkach, powinieneś zobaczyć nowy wpis:

response:

```json
{
   "systemTime": "2026-04-25T16:57:26.699522200",
   "activeTasksInfo": {
      "ERROR.throwErrorAsync": {
         "timestamp": 1777129021051,
         "exception": "RuntimeException",
         "message": "KATASTROFA W TLE",
         "thread": "bulkTaskExecutorZapierdala--1"
      }
   }
}
```