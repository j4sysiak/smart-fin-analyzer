package pl.edu.praktyki.operation

import com.github.tomakehurst.wiremock.WireMockServer
import org.springframework.beans.factory.annotation.Autowired
import pl.edu.praktyki.BaseIntegrationSpec

import java.util.concurrent.TimeUnit

import static com.github.tomakehurst.wiremock.client.WireMock.*
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options
import static org.awaitility.Awaitility.await

class BatchOperationServiceSpec extends BaseIntegrationSpec {

    @Autowired
    BatchOperationService batchOperationService

    @Autowired
    BankOperationClient bankOperationClient

    @Autowired
    OperationRepository operationRepository

    @Autowired
    OperationBatchAuditListener operationBatchAuditListener

    WireMockServer mockServer

    def setup() {
        operationBatchAuditListener.reset()
        mockServer = new WireMockServer(options().dynamicPort())
        mockServer.start()

        // Przekierowanie klienta na dynamiczny port WireMocka
        /*
        Ta linia: `bankOperationClient.mockServerUrl = mockServer.baseUrl()`
        robi **przekierowanie klienta HTTP na lokalny mock serwer**.

        ### Co to oznacza
        - `mockServer.baseUrl()` zwraca adres uruchomionego WireMocka, np. `http://localhost:12345`
        - ten adres jest przypisywany do pola `mockServerUrl` w `bankOperationClient`
        - od tego momentu `bankOperationClient` wysyła żądania do WireMocka, a nie do prawdziwego API

        ### Po co to jest w teście
        WireMock startuje na **dynamicznym porcie**, więc adres nie jest znany wcześniej.
        Dlatego po uruchomieniu serwera test wstrzykuje ten aktualny adres do klienta.

        ### Efekt
        Gdy `batchOperationService.processAll()` wywoła endpointy typu:
        - `/api/batch/deposits`
        - `/api/batch/withdrawals`

        to trafią one do zdefiniowanych stubów w teście, a nie do prawdziwego API. Dzięki temu test jest:
        - izolowany
        - powtarzalny
        - niezależny od zewnętrznego systemu
         */
        bankOperationClient.mockServerUrl = mockServer.baseUrl()


        /*
        To oznacza zdefiniowanie `stuba` w WireMocku w pliku:
        `src/test/groovy/pl/edu/praktyki/operation/BatchOperationServiceSpec.groovy`.

        **Co robi ten fragment:**
        - przechwytuje żądanie HTTP `GET` na endpoint `/api/batch/deposits`,
        - zwraca odpowiedź `200`,
        - ustawia nagłówek `Content\-Type: application/json`,
        - odsyła JSON z 2 operacjami typu `DEPOSIT`.

        **W praktyce:**
        test udaje zewnętrzne API bankowe, żeby `batchOperationService.processAll()` mógł pobrać dane bez wywoływania prawdziwego serwera.

        **Znaczenie poszczególnych elementów:**
        - `stubFor(...)` \- rejestruje zachowanie mocka,
        - `get(urlEqualTo(...))` \- dopasowuje dokładnie żądanie `GET` pod wskazany URL,
        - `willReturn(aResponse())` \- definiuje odpowiedź,
                - `withStatus(200)` \- odpowiedź poprawna,
                - `withHeader(...)` \- nagłówek odpowiedzi,
                - `withBody(...)` \- ciało odpowiedzi w formacie JSON.

        **Efekt w teście:**
        jeśli kod aplikacji wywoła `/api/batch/deposits`, dostanie dokładnie te 2 rekordy depozytów.
         */
        // 1) deposits -> 2 rekordy
        mockServer.stubFor(get(urlEqualTo("/api/batch/deposits"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
[
  {
    "operationId": "OP-D-001",
    "operationType": "DEPOSIT",
    "targetAccount": "PL001",
    "amount": 100.00,
    "sourceCurrency": "PLN",
    "correlationId": "BATCH-1"
  },
  {
    "operationId": "OP-D-002",
    "operationType": "DEPOSIT",
    "targetAccount": "PL002",
    "amount": 250.00,
    "sourceCurrency": "PLN",
    "correlationId": "BATCH-1"
  }
]
""")))

        mockServer.stubFor(get(urlEqualTo("/api/batch/withdrawals"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
[
  {
    "operationId": "OP-W-001",
    "operationType": "WITHDRAWAL",
    "sourceAccount": "PL003",
    "amount": 50.00,
    "sourceCurrency": "PLN",
    "correlationId": "BATCH-1"
  }
]
""")))

        mockServer.stubFor(get(urlEqualTo("/api/batch/transfers"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
[
  {
    "operationId": "OP-T-001",
    "operationType": "TRANSFER",
    "sourceAccount": "PL004",
    "targetAccount": "PL005",
    "amount": 75.00,
    "sourceCurrency": "PLN",
    "correlationId": "BATCH-1"
  }
]
""")))

        // poprawna + błędna konwersja (druga bez fxRate)
        mockServer.stubFor(get(urlEqualTo("/api/batch/conversions"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
[
  {
    "operationId": "OP-C-001",
    "operationType": "CONVERSION",
    "sourceAccount": "PL006",
    "amount": 10.00,
    "sourceCurrency": "EUR",
    "targetCurrency": "PLN",
    "fxRate": 4.25,
    "correlationId": "BATCH-1"
  },
  {
    "operationId": "OP-C-002",
    "operationType": "CONVERSION",
    "sourceAccount": "PL007",
    "amount": 10.00,
    "sourceCurrency": "EUR",
    "targetCurrency": "PLN",
    "correlationId": "BATCH-1"
  }
]
""")))
    }

    def cleanup() {
        mockServer?.stop()
    }

    def "powinien pobrać operacje z mockservera i zapisać je do operations"() {
        when:
        def summary = batchOperationService.processAll()

        then:
        summary.total == 6
        summary.saved == 5
        summary.skipped == 0
        summary.failed == 1

        and:
        await().atMost(5, TimeUnit.SECONDS).until {
            operationBatchAuditListener.getProcessedCount() == 1
        }

        and:
        operationRepository.count() == 5
        operationRepository.findByOperationId("OP-D-001").present
        operationRepository.findByOperationId("OP-D-002").present
        operationRepository.findByOperationId("OP-W-001").present
        operationRepository.findByOperationId("OP-T-001").present
        operationRepository.findByOperationId("OP-C-001").present
        // //OP-C-002 jest błędny, bo nie ma fxRate, walidator powienien go odrzucić
        operationRepository.findByOperationId("OP-C-002").isEmpty()
    }

    def "powinien pominąć duplikaty po operationId"() {

        /*
        Ta linia jest potrzebna do **przygotowania stanu testu**.
        Pierwsze wywołanie `processAll()`:

        - pobiera operacje z WireMocka,
        - zapisuje je do bazy przez `operationRepository`,
        - sprawia, że kolejne wywołanie dostanie **te same `operationId`**.

        Dzięki temu drugie wywołanie w sekcji `when` sprawdza, czy serwis:

        - wykrywa duplikaty,
        - **nie zapisuje ich ponownie**,
        - zwiększa `skipped`, a nie `saved`.

        Bez tej linii drugi test nie sprawdzałby duplikatów, tylko znowu zwykły pierwszy zapis.
        */
        given:
        batchOperationService.processAll()

        when:
        def summary = batchOperationService.processAll()

        then:
        summary.total == 6
        summary.saved == 0
        summary.skipped == 5
        summary.failed == 1

        and:
        operationRepository.count() == 5
    }

    def "powinien publikować event z poprawnym triggerem dla processType(#operationType)"() {
        when:
        def summary = batchOperationService.processType(operationType)

        then:
        summary.total == expectedTotal
        summary.saved == expectedSaved
        summary.skipped == 0
        summary.failed == expectedFailed

        and:
        await().atMost(5, TimeUnit.SECONDS).until {
            operationBatchAuditListener.getProcessedCount() == 1 &&
                    operationBatchAuditListener.getLastTrigger() == expectedTrigger
        }

        where:
        operationType | expectedTrigger | expectedTotal | expectedSaved | expectedFailed
        //"abc"         | "ABC"           | 0             | 0             | 0
        //"deposit"     | "DEPOSIT"       | 2             | 2             | 0
        "WITHDRAWAL"  | "WITHDRAWAL"    | 1             | 1             | 0
        //"TRANSFER"    | "TRANSFER"      | 1             | 1             | 0
        //"conversion"  | "CONVERSION"    | 2             | 1             | 1
    }
}