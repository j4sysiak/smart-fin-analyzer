package pl.edu.praktyki.facade

import org.springframework.beans.factory.annotation.Autowired
import pl.edu.praktyki.BaseIntegrationSpec
import pl.edu.praktyki.domain.Transaction
import pl.edu.praktyki.service.ThreadTracker

import java.time.LocalDate
import java.util.concurrent.TimeUnit

import static org.awaitility.Awaitility.await

class SmartFinFacadeAsyncSpec extends BaseIntegrationSpec {

    @Autowired SmartFinFacade facade
    @Autowired ThreadTracker threadTracker

    def "powinien uruchomić ciężkie przetwarzanie w tle i zarejestrować wątek z puli bulk"() {
        given: "przygotowane dane wejściowe"
        def user = "FacadeAsyncTester"
        def transactions = [
                new Transaction(id: "T-ASYNC-1",
                                amount: 1000.0,
                                category: "Test",
                                date: LocalDate.now())]

        def rules = ["addTag('PROCESSED_IN_BACKGROUND')"]

        when: "wywołujemy metodę fasady (powinna wrócić natychmiast dzięki @Async)"
        long startTime = System.currentTimeMillis()
        facade.processInBackgroundTask(user, transactions, rules)
        long duration = System.currentTimeMillis() - startTime

        then: "1. Wywołanie nie zablokowało testu (trwało ułamek sekundy)"
        duration < 1000

        then: "2. Czekamy asynchronicznie, aż wątek tła zapisze się w ThreadTrackerze"
        await().atMost(10, TimeUnit.SECONDS).until {
            threadTracker.get('SmartFinFacade.processInBackgroundTask') != null
        }

        and: "3. Weryfikujemy metadane zapisane przez wątek asynchroniczny"
        Map stats = threadTracker.get('SmartFinFacade.processInBackgroundTask') as Map

        stats.thread != null
        // Sprawdzamy czy prefiks wątku zgadza się z Twoją konfiguracją w AsyncConfig
        stats.thread.startsWith("bulkTaskExecutorZapierdala--")

        and: "wyświetlamy diagnostykę wątków"
        println "----------------------------------------------------------------"
        println "METRYKI ASYNCHRONICZNE FASADY:"
        println "Uruchomiono przez wątek: ${Thread.currentThread().name} (powinien być Test worker)"
        println "Przetworzono w tle przez: ${stats.thread}"
        println "Czas odpowiedzi API: ${duration}ms"
        println "----------------------------------------------------------------"
    }
}