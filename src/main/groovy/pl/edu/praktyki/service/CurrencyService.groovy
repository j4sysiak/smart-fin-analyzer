package pl.edu.praktyki.service

import groovy.util.logging.Slf4j
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Service
import groovy.json.JsonSlurper
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker
import org.springframework.beans.factory.annotation.Value // DODAJ IMPORT

@Service
@Slf4j
class CurrencyService {

    private final HttpClient client = HttpClient.newHttpClient()
    private final JsonSlurper slurper = new JsonSlurper()

    // Zaciągamy URL z konfiguracji Springa
    /*
    @Value: To adnotacja ze Spring Framework, która wstrzykuje wartość z konfiguracji do pola apiUrl.
    Oto jak działa:
    ${currency.api.url} — Spring szuka właściwości currency.api.url w plikach konfiguracyjnych (application.properties / application.yml, zmiennych środowiskowych itp.).
    :https://open.er-api.com/v6/latest/PLN — część po dwukropku to wartość domyślna.
    Jeśli Spring nie znajdzie `currency.api.url` w żadnej konfiguracji, użyje tego URL-a.
    Dzięki temu URL API nie jest zahardkodowany w kodzie — można go łatwo zmienić np. w application.yml:
    bez konieczności modyfikacji kodu źródłowego. To ułatwia też testowanie — w testach można podać mockowy URL.
    */
    @Value('${currency.api.url:https://open.er-api.com/v6/latest/PLN}')
    private String apiUrl

    /**
     * Pobiera aktualny kurs wymiany dla danej waluty względem PLN.
     * W prawdziwym systemie warto tu dodać @Cacheable (Spring Cache).
     */
    @Cacheable("exchangeRates") // Spring zapamięta wynik dla każdego unikalnego 'fromCurrency'
    // Jeśli metoda wybuchnie 3 razy pod rząd, wyłącznik się "otworzy"
    @CircuitBreaker(name = "currencyApi", fallbackMethod = "fallbackRate")
    BigDecimal getExchangeRate(String fromCurrency) {

        if (fromCurrency == "PLN") return 1.0  // easy case: PLN -> PLN

        // Zamiast println używamy log.info lub log.debug
        log.info(">>> [API CALL] Pobieram kurs z internetu dla: {}", fromCurrency)

        //try {
            // UŻYWAMY ZMIENNEJ ZAMIAST TEKSTU NA SZTYWNO!
            def request = HttpRequest.newBuilder()
                    //.uri(URI.create("https://open.er-api.com/v6/latest/PLN")) // API zwraca kursy względem PLN, więc zawsze pytamy o PLN jako bazę
                    .uri(URI.create(apiUrl))
                    .GET()
                    .build()

            def response = client.send(request, HttpResponse.BodyHandlers.ofString())
            def json = slurper.parseText(response.body())

            // API zwraca kursy względem bazy (PLN).
            // np. jeśli 1 PLN = 0.23 EUR, to kurs EUR -> PLN to 1 / 0.23
            def rateToPln = json.rates[fromCurrency]

            // Zamiast return null, rzucamy wyjątek, żeby obudzić Circuit Breakera!
            if (rateToPln == null) {
                throw new IllegalArgumentException("Nieznana waluta: $fromCurrency")
            }

            return rateToPln ? (1.0 / rateToPln).toBigDecimal() : 1.0

        //} catch (Exception e) {
        //    // Logowanie błędu ze stacktracem
        //    log.error("Błąd pobierania kursu dla waluty {}: {}", fromCurrency, e.message)
        //    // W razie błędu sieciowego nadal możemy rzucić wyjątek lub zwrócić null
        //    return null
        //}

        return 1.0 // fallback (jeśli try się nie udał i nie było returna)
    }

    // --- FALLBACK METHOD ---
    // Musi mieć dokładnie takie same argumenty + Throwable na końcu
    BigDecimal fallbackRate(String fromCurrency, Throwable t) {
        log.warn(">>> [CIRCUIT BREAKER] Uruchomiono Fallback! Powód: {}", t.message)
        // Zwracamy bezpieczną wartość zastępczą
        return 4.0.toBigDecimal()
    }
}