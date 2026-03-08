package pl.edu.praktyki.service

import spock.lang.Specification
import static org.awaitility.Awaitility.await
import static java.util.concurrent.TimeUnit.SECONDS

class AsyncServiceSpec extends Specification {

    def asyncService = new AsyncService()

    def "powinien poczekać aż rekord pojawi się w bazie asynchronicznie"() {
        when: "wywołujemy metodę w tle"
        // symulacja długotrwałej operacji, która po 1.5 sekundzie zapisuje rekord do "bazy"
        asyncService.performActionAsync("TX-1", "PROCESSED")

        then: "używamy await, aby czekać na pojawienie się rekordu"
        // await() sprawdza warunek wielokrotnie, dopóki nie przejdzie
        // Jeśli nie przejdzie w ciągu 3 sekund, test oblewa.
        // Jeśli asyncService jest nullem, to rzuci NPE przed wejściem do await
        await().atMost(3, SECONDS).until {
            asyncService.exists("TX-1")
        }

        and: "dodatkowa weryfikacja wartości"
        asyncService.db["TX-1"] == "PROCESSED"
    }
}