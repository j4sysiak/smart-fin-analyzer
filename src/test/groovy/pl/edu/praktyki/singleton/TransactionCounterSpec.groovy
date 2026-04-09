package pl.edu.praktyki.singleton

import groovyx.gpars.GParsPool
import static org.awaitility.Awaitility.await
import java.util.concurrent.TimeUnit
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.test.context.ActiveProfiles
import pl.edu.praktyki.BaseIntegrationSpec
import pl.edu.praktyki.repository.Counter
import pl.edu.praktyki.repository.CounterRepository


// 1. @ActiveProfiles(value = ["local-pg"], inheritProfiles = false) powoduje,
//    że kontekst testowy ładuje profil local-pg i w efekcie użyje ustawień z pliku application-local-pg.properties
//    (albo application-local-pg.yml) zamiast domyślnych.
// 2. Test uruchamia pełny kontekst Springa (MockMvc + repozytoria) i będzie próbował połączyć się
//    z bazą zgodnie z ustawieniami z tego pliku.
//    Komentarz w kodzie słusznie mówi: musisz mieć lokalnego Postgresa uruchomionego — test tego serwera
//    nie uruchomi samodzielnie.
// 3. inheritProfiles = false oznacza, że tylko local-pg jest aktywny (inne profile domyślne nie są łączone).
// 4. H2 nie będzie działać w tym projekcie (jak napisano), więc albo uruchom lokalny Postgres,
//    albo użyj Testcontainers / wbudowanego Postgresa, jeśli chcesz automatycznie startować DB w testach.
// 5. Sprawdź w application-local-pg.properties URL, username i password oraz czy schemat/bazy zostały przygotowane przed uruchomieniem testów.


@AutoConfigureMockMvc

//KNOW HOW!  (ActiveProfiles = 'tc')
// To nie dziala, żeby użyć Testcontainers - profil 'tc', to BaseIntegrationSpec ustaw warunek na if (1==1)
// wtedy zawsze będzie używał Testcontainers.
// Wtedy ten test będzie działał bez konieczności uruchamiania ręcznie Postgresa na Docker.
// wtedy postgres będzie uruchamiany automatycznie w kontenerze Docker przez Testcontainers,
// a po zakończeniu testów będzie automatycznie zatrzymywany i usuwany.

//@ActiveProfiles(value = ["tc"], inheritProfiles = false)


//KNOW HOW!  (ActiveProfiles = 'local-pg')
// Wymusi użycie application-local-pg.properties ale musisz mieć wlączony lokalny Postgresa!
// (nie działa z H2, bo H2 nie obsługuje funkcji SQL, których używamy w repozytorium)
// tutaj info jak uruchomić lokalnego postgresa na dokerze dla profilu: local-pg:
//                     C:\dev\smart-fin-analyzer\src\test\resources\application-local-pg.properties

@ActiveProfiles("tc") // use Testcontainers for tests (start PostgreSQL container automatically)
class TransactionCounterSpec extends BaseIntegrationSpec {

    @Autowired
    TransactionCounterService counterService

    @Autowired
    CounterRepository counterRepository

    def setup() {
        // przygotuj rekord w bazie przed testem
        counterRepository.deleteAll()
        counterRepository.save(new Counter(name: "requests", value: 0))
    }

    def "baza danych powinna bezpiecznie inkrementować licznik wielowątkowo"() {
        when: "wiele równoległych wątków inkrementuje ten sam licznik w bazie"
        // Zmniejszamy domyślną liczbę iteracji, żeby test był stabilniejszy na CI/maszynach deweloperskich.
        // Możesz zwiększyć do 10000 lokalnie jeśli chcesz testować wydajność.
        int increments = 1000

        GParsPool.withPool {
            (1..increments).collectParallel {
                counterService.increment("requests")
            }
        }

        then: "wynik ostateczny będzie równy liczbie inkrementów (czekamy, aż baza się zaktualizuje)"
        // Używamy Awaitility — czekamy do 30s, aż licznik osiągnie oczekiwaną wartość.
        await().atMost(30, TimeUnit.SECONDS).until {
            counterService.getCounter("requests") == increments
        }
    }
}