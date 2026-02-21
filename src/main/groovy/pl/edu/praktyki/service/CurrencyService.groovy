package pl.edu.praktyki.service

import org.springframework.stereotype.Service
import groovy.json.JsonSlurper
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

@Service
class CurrencyService {

    private final HttpClient client = HttpClient.newHttpClient()
    private final JsonSlurper slurper = new JsonSlurper()

    /**
     * Pobiera aktualny kurs wymiany dla danej waluty względem PLN.
     * W prawdziwym systemie warto tu dodać @Cacheable (Spring Cache).
     */
    BigDecimal getExchangeRate(String fromCurrency) {
        if (fromCurrency == "PLN") return 1.0  // easy case: PLN -> PLN

        try {
            def request = HttpRequest.newBuilder()
                    .uri(URI.create("https://open.er-api.com/v6/latest/PLN"))
                    .GET()
                    .build()

            def response = client.send(request, HttpResponse.BodyHandlers.ofString())
            def json = slurper.parseText(response.body())

            // API zwraca kursy względem bazy (PLN).
            // np. jeśli 1 PLN = 0.23 EUR, to kurs EUR -> PLN to 1 / 0.23
            def rateToPln = json.rates[fromCurrency]

            // POPRAWKA: Jeśli waluty nie ma w mapie 'rates', zwracamy null
            if (rateToPln == null) return null

            return rateToPln ? (1.0 / rateToPln).toBigDecimal() : 1.0
        } catch (Exception e) {
            // W razie błędu sieciowego nadal możemy rzucić wyjątek lub zwrócić null
            return null
        }
    }
}