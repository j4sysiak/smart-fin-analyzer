package pl.edu.praktyki.operation

import groovy.json.JsonSlurper
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

/**
 * Cel: klasa, która puka HTTP"em do MockServera i zwraca listę operacji jako List<OperationDto>.
 * Klient HTTP do MockServera operacji wsadowych.
 * Pobiera listy operacji z 4 endpointów:
 *   GET /api/batch/deposits
 *   GET /api/batch/withdrawals
 *   GET /api/batch/transfers
 *   GET /api/batch/conversions
 */
@Slf4j
@Component
class BankOperationClient {

    // W application.properties: bank.operations.mock-server-url=http://localhost:8095
    @Value('${bank.operations.mock-server-url:http://localhost:8095}')
    String mockServerUrl

    List<OperationDto> fetchDeposits() {
        fetchOperations("deposits", "DEPOSIT")
    }

    List<OperationDto> fetchWithdrawals() {
        fetchOperations("withdrawals", "WITHDRAWAL")
    }

    List<OperationDto> fetchTransfers() {
        fetchOperations("transfers", "TRANSFER")
    }

    List<OperationDto> fetchConversions() {
        fetchOperations("conversions", "CONVERSION")
    }

    // Pobiera wszystkie 4 typy i zwraca jedną listę
    List<OperationDto> fetchAll() {
        def all = []
        all += fetchDeposits()
        all += fetchWithdrawals()
        all += fetchTransfers()
        all += fetchConversions()
        return all
    }

    // --- prywatna metoda robiąca właściwy HTTP GET ---
    private List<OperationDto> fetchOperations(String endpoint, String operationType) {
        def url = "${mockServerUrl}/api/batch/${endpoint}"
        log.info("Pobieranie operacji z: {}", url)

        try {
            HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection()
            conn.requestMethod = "GET"
            conn.connectTimeout = 5000
            conn.readTimeout    = 10000

            int status = conn.responseCode

            if (status == 404) {
                log.warn("Brak operacji na endpoincie {} (404)", url)
                return []
            }

            if (status >= 400) {
                log.error("Błąd HTTP {} dla endpointu {}", status, url)
                return []
            }

            String body = conn.inputStream.getText("UTF-8")
            def parsed  = new JsonSlurper().parseText(body)

            // MockServer może zwrócić { "operations": [...] } lub bezpośrednio [...]
            def items = (parsed instanceof List) ? parsed : parsed.operations ?: []

            return items.collect { Map item ->
                def dto = new OperationDto(item)
                dto.operationType = item.operationType ?: operationType
                dto
            }

        } catch (ConnectException e) {
            log.error("Nie można połączyć się z MockServerem pod adresem {}: {}", url, e.message)
            return []
        } catch (Exception e) {
            log.error("Nieoczekiwany błąd przy pobieraniu operacji z {}: {}", url, e.message)
            return []
        }
    }
}