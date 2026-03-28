package pl.edu.praktyki.service

import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service
import groovy.util.logging.Slf4j
import pl.edu.praktyki.event.TransactionBatchProcessedEvent
import java.util.concurrent.atomic.AtomicInteger // DODAJ TO

@Service
@Slf4j
class AsyncNotificationService {

    // HACZYK: Licznik do celów testowych (AtomicInteger jest bezpieczny dla wielu wątków)
    // Dodamy licznik, który pozwoli nam sprawdzić, ile razy serwis został wywołany.
    // Musimy dodać do serwisu mały "haczyk" (licznik lub flagę), a w teście użyć Awaitility,
    // aby poczekać, aż asynchroniczna magia się wydarzy.
    // Zmieniamy na private
    private final AtomicInteger processedEventsCount = new AtomicInteger(0)

    // Dodajemy metodę - dzięki niej Proxy będzie wiedziało, skąd wziąć wartość
    int getProcessedCount() {
        return processedEventsCount.get()
    }

    // Aadnotacje takie jak @Async czy @Transactional tworzą "opakowanie" wokół Twojej klasy.
    @Async("bulkTaskExecutor") // Używamy Twojej puli wątków z Lab 68
    @EventListener
    void handleBatchEvent(TransactionBatchProcessedEvent event) {
        log.info(">>> [ASYNC-EVENT] Rozpoczynam wysyłkę raportu do systemu zewnętrznego dla: {}", event.userName)

        // Symulujemy ciężką pracę (np. generowanie PDF i wysyłka maila)
        sleep(2000)

        log.info(">>> [ASYNC-EVENT] Raport o bilansie {} PLN został pomyślnie przetworzony w tle.", event.totalBalance)

        processedEventsCount.incrementAndGet()
    }

    // Opcjonalnie metoda do resetowania licznika między testami
    void reset() {
        processedEventsCount.set(0)
    }

}