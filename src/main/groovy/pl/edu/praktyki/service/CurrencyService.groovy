package pl.edu.praktyki.service

import org.springframework.stereotype.Service
import groovy.json.JsonSlurper
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

@Service
class CurrencyService {

    private final HttpClient client
    private final JsonSlurper slurper = new JsonSlurper()

    CurrencyService() {
        this.client = HttpClient.newHttpClient()
    }

    // Konstruktor do wstrzykiwania HttpClient (np. w testach)
    CurrencyService(HttpClient client) {
        this.client = client
    }

    /**
     * Pobiera aktualny kurs wymiany dla danej waluty względem PLN.
     * W prawdziwym systemie warto tu dodać @Cacheable (Spring Cache).
     */
    BigDecimal getExchangeRate(String fromCurrency) {
        if (fromCurrency == "PLN") return 1.0

        try {
            def request = HttpRequest.newBuilder()
                    .uri(URI.create("https://open.er-api.com/v6/latest/PLN"))
                    .GET()
                    .build()

            def response = client.send(request, HttpResponse.BodyHandlers.ofString())
            def json = slurper.parseText(response.body())

            // API zwraca kursy względem bazy (PLN).
            // Jeśli 1 PLN = 0.23 EUR, to kurs EUR -> PLN to 1 / 0.23
            def rateToPLN = json.rates[fromCurrency]
            return rateToPLN ? (1.0 / rateToPLN).toBigDecimal() : 1.0
        } catch (Exception e) {
            println "Błąd pobierania kursu: ${e.message}. Używam kursu 1.0"
            return 1.0
        }
    }

    /**
     * Przelicza kwotę z podanej waluty na PLN.
     */
    BigDecimal convertToPLN(BigDecimal amount, String fromCurrency) {
        return amount * getExchangeRate(fromCurrency)
    }
}