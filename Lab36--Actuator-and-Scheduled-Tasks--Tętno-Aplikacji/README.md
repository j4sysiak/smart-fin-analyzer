Lab 36
------

Z punktu widzenia programisty (Developera) aplikacja jest gotowa. 
Ale z punktu widzenia zespołu DevOps / SRE (utrzymania), brakuje jej jeszcze dwóch rzeczy, by można było ją wdrożyć na produkcję:

`Monitorowania (Healthchecks)` – czy aplikacja żyje i ma połączenie z bazą?

Lab 36: Actuator & Scheduled Tasks (Tętno Aplikacji)
----------------------------------------------------

Cel: 
Dodanie endpointów monitorujących system oraz stworzenie zadania, które cyklicznie (np. co 10 sekund na potrzeby testu) wykonuje pracę w tle.

Krok 36.1: Dodanie Spring Boot Actuator
---------------------------------------

Actuator to potężny moduł Springa, który automatycznie wystawia ukryte endpointy REST do monitorowania pamięci RAM, bazy danych, czy statusu serwera.
Otwórz build.gradle i dodaj:

// Metryki, Healthchecki i monitoring dla produkcji
implementation 'org.springframework.boot:spring-boot-starter-actuator'

Otwórz `src/main/resources/application.properties` i pozwól Actuatorowi pokazać swoje dane:
```
# Wystawiamy wszystkie endpointy monitorujące przez HTTP
management.endpoints.web.exposure.include=*
# Pokazujemy szczegóły zdrowia aplikacji (np. status dysku i bazy H2)
management.endpoint.health.show-details=always
```


Krok 36.2: Włączenie Zadań Cyklicznych (@EnableScheduling)
----------------------------------------------------------

Musimy powiedzieć Springowi: "Uruchom swój wewnętrzny zegar".
Otwórz `SmartFinDbApp.groovy` i dodaj jedną adnotację na samej górze:

```groovy
import org.springframework.scheduling.annotation.EnableScheduling // DODAJ IMPORT

@SpringBootApplication
@EnableCaching
@EnableScheduling // <-- DODAJ TO
class SmartFinDbApp {
// ...
```



Krok 36.3: Stworzenie "Nocnego" Raportera (DailyReportScheduler.groovy)
-----------------------------------------------------------------------
Stworzymy serwis, który uruchamia się sam, bez klikania w przeglądarce i bez wpisywania komend.
Stwórz plik `src/main/groovy/pl/edu/praktyki/service/DailyReportScheduler.groovy`:

```groovy
package pl.edu.praktyki.service

import org.springframework.stereotype.Service
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.scheduling.annotation.Scheduled
import groovy.util.logging.Slf4j
import pl.edu.praktyki.repository.TransactionRepository

@Service
@Slf4j
class DailyReportScheduler {

    @Autowired TransactionRepository repo

    // fixedRate = 10000 oznacza, że metoda odpali się co 10 sekund (dla testów).
    // Na produkcji użylibyśmy np.: @Scheduled(cron = "0 0 0 * * ?") -> codziennie o północy.
    @Scheduled(fixedRate = 10000)
    void generateAutomaticReport() {
        log.info("=== [AUTOMATYZACJA] Uruchamiam cykliczny przegląd bazy danych ===")
        
        long count = repo.count()
        if (count == 0) {
            log.info("Baza jest pusta. Czekam na nowe transakcje...")
            return
        }

        // Pobieramy transakcje z bazy i robimy szybką statystykę
        def allTx = repo.findAll()
        def totalPln = allTx*.amountPLN.sum()

        log.info("Obecny stan systemu: {} zapisanych transakcji.", count)
        log.info("Całkowity bilans użytkowników: {} PLN", totalPln)
        log.info("==================================================================")
    }
}
```

Jak to przetestować? (Efekt WOW)
Część 1: Scheduler (Zegar)
Uruchom aplikację z parametrami (żeby wgrały się początkowe dane do bazy):

`./gradlew runSmartFinDb -PappArgs="-u Jacek"`

Zostaw terminal włączony (pamiętaj, Spring działa w tle).
Zegarek w rękę! Co równe 10 sekund w Twojej konsoli samoistnie pojawi się piękny, zielony log z DailyReportScheduler raportujący stan bazy.


Część 2: Actuator (Monitoring)
Gdy aplikacja tak sobie "tyka" w tle, otwórz przeglądarkę i wejdź pod adres:
👉 http://localhost:8080/actuator/health
Zobaczysz JSON informujący, że aplikacja ma status UP, dysk ma miejsce (diskSpace: UP), a baza H2 odpowiada (db: UP). To właśnie ten adres firmy podają systemom takim jak Kubernetes, aby wiedział, czy aplikacja jest zdrowa.
Otwórz jeszcze:
👉 http://localhost:8080/actuator/metrics/system.cpu.usage
Zobaczysz aktualne zużycie procesora przez Twój program!
Co to znaczy dla Ciebie?
Gdy mówisz na rekrutacji: "Stworzyłem REST API", brzmisz jak Junior.
Gdy mówisz: "Stworzyłem REST API, które ma endpointy Actuatora do Liveness Probe w Kubernetesie i wbudowanego Schedulera do nocnych batch-jobów", brzmisz jak w pełni ukształtowany Mid/Senior.
Daj znać, czy Twój zegar zaczął tykać i czy Actuator żyje! ⏱️📊






