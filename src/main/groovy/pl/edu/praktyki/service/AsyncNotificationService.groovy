package pl.edu.praktyki.service

import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Async
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import groovy.util.logging.Slf4j
import pl.edu.praktyki.event.TransactionBatchProcessedEvent
import java.util.concurrent.atomic.AtomicInteger

@Service
@Slf4j
class AsyncNotificationService {

    // DODAJEMY TAKI HACZYK: tj. Licznik do celów testowych (AtomicInteger jest bezpieczny dla wielu wątków)
    // Dodamy licznik, który pozwoli nam sprawdzić, ile razy serwis został wywołany.
    // Musimy dodać do serwisu mały "haczyk" (licznik lub flagę), a w teście użyć Awaitility,
    // aby poczekać, aż asynchroniczna magia się wydarzy.
    // Zmieniamy na private, żeby można bylo użyć gettera: `getProcessedCount()` do odczytu w teście,
    // ale nie pozwalamy na bezpośrednią modyfikację z zewnątrz.
    private final AtomicInteger processedEventsCount = new AtomicInteger(0)

    @Autowired
    ThreadTracker threadTracker

    // Dodajemy metodę - dzięki niej Proxy będzie wiedziało, skąd wziąć wartość
    int getProcessedCount() {
        return processedEventsCount.get()
    }

// Adnotacje takie jak @Async czy @Transactional tworzą "opakowanie" wokół Twojej klasy.
// Chodzi o klasę będącą beanem Springa — czyli klasę zarządzaną przez kontener
// (np. oznaczoną @Component, @Service, @Repository, @Configuration albo zdefiniowaną jako @Bean).
// Adnotacje takie jak @Async czy @Transactional działają przez utworzenie proxy wokół tego beana
// i przechwytywanie wywołań metod przychodzących z zewnątrz.

    // Włącza obsługę adnotacji @Async.
    // Dzięki temu możesz oznaczać metody (tak jak tutaj ta metoda) jako asynchroniczne, a Spring będzie je wykonywał w osobnych wątkach.

    //Adnotacja @Async("bulkTaskExecutor") mówi Springowi,
    // żeby uruchomić metodę asynchronicznie używając beana o tej nazwie
    // (zazwyczaj ThreadPoolTaskExecutor skonfigurowanego w src/main/groovy/pl/edu/praktyki/config/AsyncConfig.groovy).
    // Jeśli nie zdefiniujesz beana o tej nazwie, musisz go dodać albo użyć domyślnego executora (albo usunąć nazwę z @Async`).

    // `bulkTaskExecutor`  w tej adnotacji to nazwa beana typu `Executor / TaskExecutor` zarejestrowanego w kontenerze Spring.
    // W @Async("bulkTaskExecutor") wskazujesz, że ta metoda ma być uruchomiona asynchronicznie w wątkach tej konkretnej puli
    // — to nie służy do synchronizacji, tylko do wyboru puli wątków.

    // Przykład konfiguracji takiego beana typu `Executor / TaskExecutor` o nazwie `bulkTaskExecutor` znajdziesz w pliku AsyncConfig.groovy, który powinieneś mieć w projekcie.:
    // u mnie ta klasa AsyncConfig jest tu: C:\dev\smart-fin-analyzer\src\main\groovy\pl\edu\praktyki\config\AsyncConfig.groovy
    /*
    @Configuration
    @EnableAsync
    public class AsyncConfig {

        @Bean(name = "bulkTaskExecutor")
        public Executor bulkTaskExecutor() {
            ThreadPoolTaskExecutor exec = new ThreadPoolTaskExecutor();
            exec.setCorePoolSize(5);
            exec.setMaxPoolSize(10);
            exec.setQueueCapacity(100);
            exec.setThreadNamePrefix("bulk-");
            exec.initialize();
            return exec;
        }
    }*/

    // Uwaga: jeśli nie zdefiniujesz beana o takiej nazwie,
    // Spring nie będzie miał wskazanej puli i trzeba albo dodać beana,
    // albo użyć domyślnego executor`a (albo usunąć nazwę z @Async).

    @Async("bulkTaskExecutor") // Używamy puli wątków: `bulkTaskExecutor` to nazwa beana typu Executor/TaskExecutor (czyli puli wątków).
    @EventListener
    void handleBatchEvent(TransactionBatchProcessedEvent event) {

        log.info(">>> [ASYNCHRONICZNY-EVENT] Rozpoczynam wysyłkę raportu do systemu zewnętrznego dla: {}", event.userName)
        // Zapisujemy który wątek przetwarza event (użyteczne w testach/diagnostyce)
        threadTracker.put('AsyncNotificationService.handleBatchEvent', [thread: Thread.currentThread().name,
                                                                        ts: System.currentTimeMillis(),
                                                                        user: event?.userName,
                                                                        count: event?.transactionsCount])

        // Symulujemy ciężką pracę (np. generowanie PDF i wysyłka maila)
        sleep(6000)

        log.info(">>> [ASYNCHRONICZNY-EVENT] Raport o bilansie {} PLN został pomyślnie przetworzony w tle.", event.totalBalance)

        // Każdy event budzi 3 słuchaczy (Audit, Notification, Projector).
        // Wszystkie korzystają z tej samej puli bulkTaskExecutor.
        processedEventsCount.incrementAndGet()

        // Zapisujemy czas zakończenia per-user — używane w AsyncNotificationSpec do izolacji testów:
        // pozwala czekać na zakończenie KONKRETNEGO eventu bez ryzyka pomylenia z innymi handlerami z innych testów.
        threadTracker.put("AsyncNotificationService.completed.${event?.userName}", [
                completedAt: System.currentTimeMillis(),
                thread     : Thread.currentThread().name,
                user       : event?.userName,
                count      : event?.transactionsCount
        ])
    }

    // Opcjonalnie metoda do resetowania licznika między testami
    void reset() {
        processedEventsCount.set(0)
    }

}