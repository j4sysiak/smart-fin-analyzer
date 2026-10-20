package pl.edu.praktyki.web

import com.github.tomakehurst.wiremock.WireMockServer
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import pl.edu.praktyki.BaseIntegrationSpec
import pl.edu.praktyki.operation.BankOperationClient
import pl.edu.praktyki.operation.OperationRepository

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse
import org.springframework.test.annotation.DirtiesContext
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

import pl.edu.praktyki.operation.OperationBatchAuditListener
import static org.awaitility.Awaitility.await
import java.util.concurrent.TimeUnit

@AutoConfigureMockMvc
@ActiveProfiles("tc")
@WithMockUser(username = "test-admin", roles = ["ADMIN"])
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class BatchProcessingControllerSpec extends BaseIntegrationSpec {

    @Autowired
    MockMvc mvc

    @Autowired
    BankOperationClient bankOperationClient

    @Autowired
    OperationRepository operationRepository

    @Autowired
    OperationBatchAuditListener operationBatchAuditListener

    WireMockServer mockServer

    def setup() {
        operationRepository.deleteAll()
        operationBatchAuditListener.reset()

        mockServer = new WireMockServer(options().dynamicPort())
        mockServer.start()
        bankOperationClient.mockServerUrl = mockServer.baseUrl()

        // deposits -> 2 rekordy
        mockServer.stubFor(com.github.tomakehurst.wiremock.client.WireMock.get(urlEqualTo("/api/batch/deposits"))
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

        // withdrawals -> 1 rekord
        mockServer.stubFor(com.github.tomakehurst.wiremock.client.WireMock.get(urlEqualTo("/api/batch/withdrawals"))
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

        // transfers -> 1 rekord
        mockServer.stubFor(com.github.tomakehurst.wiremock.client.WireMock.get(urlEqualTo("/api/batch/transfers"))
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

        // conversions -> 2 rekordy (1 poprawny + 1 błędny bez fxRate)
        mockServer.stubFor(com.github.tomakehurst.wiremock.client.WireMock.get(urlEqualTo("/api/batch/conversions"))
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

    def "POST /api/batches/process bez operationType powinien uruchomić ALL i zwrócić summary"() {
        when:

        /* co tu się dzieje?
            1. `MockMvc` tworzy sztuczny request HTTP POST do endpointu `/api/batches/process`
            2. Spring w kontekście testowym mapuje request
                     do kontrolera `BatchProcessingController` - `POST /api/batches/process`
            3. wywoływana jest metoda `processBatch()` w kontrolerze.
                     który wywołuje `facade.processOperationsBatch(normalizedType)` z `normalizedType = null`
            4. w `SmartFinFacade` wywoływana jest metoda `batchOperationService.processAll()`
            5. w `BatchOperationService` wywoływana jest metoda `bankOperationClient.fetchAll()`,
                            która pobiera wszystkie 4 typy operacji z WireMockServera

        Przepływ jest taki:

        1. `MockMvc` buduje sztuczny request
        2. Spring mapuje `POST /api/batches/process`
        3. wywoływana jest metoda kontrolera
        4. kontroler uruchamia logikę batcha
        5. jeśli w środku są wywołania `BankOperationClient`, to **one już idą do WireMocka**, bo w `setup()` ustawiono:
        `bankOperationClient.mockServerUrl = mockServer.baseUrl()`

        Czyli:

        - **request z linii 145** \-> do kontrolera w aplikacji testowej
        - **requesty wychodzące z klienta bankowego** \-> do lokalnego `WireMockServer` na dynamicznym porcie
*/

        def response = mvc.perform(post("/api/batches/process")
                //.with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))

        then:
        response.andExpect(status().isOk())
        response.andExpect(jsonPath('$.trigger').value("ALL"))
        response.andExpect(jsonPath('$.total').value(6))
        response.andExpect(jsonPath('$.saved').value(5))
        response.andExpect(jsonPath('$.skipped').value(0))
        response.andExpect(jsonPath('$.failed').value(1))
        response.andExpect(jsonPath('$.processedAt').exists())
    }

    def "POST /api/batches/process z operationType=deposit powinien uruchomić tylko DEPOSIT"() {
        given:
        String payload = '{"operationType":"deposit"}'

        when:
        def response = mvc.perform(post("/api/batches/process")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))

        then:
        response.andExpect(status().isOk())
        response.andExpect(jsonPath('$.trigger').value("DEPOSIT"))
        response.andExpect(jsonPath('$.total').value(2))
        response.andExpect(jsonPath('$.saved').value(2))
        response.andExpect(jsonPath('$.skipped').value(0))
        response.andExpect(jsonPath('$.failed').value(0))
        response.andExpect(jsonPath('$.processedAt').exists())
    }

    def "POST /api/batches/process z niepoprawnym operationType powinien zwrócić 400"() {
        given:
        String payload = '{"operationType":"xyz"}'

        expect:
        mvc.perform(post("/api/batches/process")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath('$.status').value(400))
                .andExpect(jsonPath('$.message').value(org.hamcrest.Matchers.containsString("Nieprawidłowy operationType")))
                .andExpect(jsonPath('$.timestamp').exists())
    }

    def "POST /api/batches/process z operationType ze spacjami powinien znormalizować do DEPOSIT"() {
        given:
        String payload = '{"operationType":"  deposit  "}'

        when:
        def response = mvc.perform(post("/api/batches/process")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))

        then:
        response.andExpect(status().isOk())
        response.andExpect(jsonPath('$.trigger').value("DEPOSIT"))
        response.andExpect(jsonPath('$.total').value(2))
        response.andExpect(jsonPath('$.saved').value(2))
        response.andExpect(jsonPath('$.skipped').value(0))
        response.andExpect(jsonPath('$.failed').value(0))
        response.andExpect(jsonPath('$.processedAt').exists())
    }

    def "GET /api/batches/last-summary powinien zwrócić 404 gdy brak danych"() {
        expect:
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .get("/api/batches/last-summary"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath('$.status').value(404))
                .andExpect(jsonPath('$.message').value(org.hamcrest.Matchers.containsString("Brak ostatniego podsumowania batcha")))
    }

    def "GET /api/batches/last-summary powinien zwrócić ostatnie summary po POST /process"() {
        when:
        mvc.perform(post("/api/batches/process")
                //.with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isOk())

        and:
        await().atMost(5, TimeUnit.SECONDS).until {
            operationBatchAuditListener.getProcessedCount() == 1 &&
                    operationBatchAuditListener.hasLastSummary()
        }

        then:
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .get("/api/batches/last-summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath('$.trigger').value("ALL"))
                .andExpect(jsonPath('$.total').value(6))
                .andExpect(jsonPath('$.saved').value(5))
                .andExpect(jsonPath('$.skipped').value(0))
                .andExpect(jsonPath('$.failed').value(1))
                .andExpect(jsonPath('$.processedAt').exists())
    }

    def "GET /api/batches/history powinien zwrócić pustą listę gdy brak danych"() {
        expect:
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .get("/api/batches/history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath('$').isArray())
                .andExpect(jsonPath('$').isEmpty())
    }

    def "GET /api/batches/history powinien zwrócić ostatnie 2 podsumowania"() {
        when:
        mvc.perform(post("/api/batches/process")
                //.with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isOk())

        mvc.perform(post("/api/batches/process")
                //.with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content('{"operationType":"deposit"}'))
                .andExpect(status().isOk())

        and:
        await().atMost(5, TimeUnit.SECONDS).until {
            operationBatchAuditListener.getProcessedCount() >= 2
        }

        then:
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .get("/api/batches/history?limit=2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath('$[0].trigger').exists())
                .andExpect(jsonPath('$[0].processedAt').exists())
                .andExpect(jsonPath('$[1].trigger').exists())
                .andExpect(jsonPath('$[1].processedAt').exists())
    }

    def "GET /api/batches/history powinien filtrowac po trigger=DEPOSIT"() {
        when:
        mvc.perform(post("/api/batches/process")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isOk())

        mvc.perform(post("/api/batches/process")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content('{"operationType":"deposit"}'))
                .andExpect(status().isOk())

        and:
        await().atMost(5, TimeUnit.SECONDS).until {
            operationBatchAuditListener.getProcessedCount() >= 2
        }

        then:
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .get("/api/batches/history?trigger=DEPOSIT&limit=10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath('$').isArray())
                .andExpect(jsonPath('$[0].trigger').value("DEPOSIT"))
                .andExpect(jsonPath('$[0].total').value(2))
                .andExpect(jsonPath('$[0].saved').value(0))   // drugi run DEPOSIT -> duplikaty
                .andExpect(jsonPath('$[0].skipped').value(2))
                .andExpect(jsonPath('$[0].failed').value(0))
                .andExpect(jsonPath('$[0].processedAt').exists())
    }

    def "GET /api/batches/history powinien zwrocic pusta liste dla nieistniejacego triggera"() {
        when:
        mvc.perform(post("/api/batches/process")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isOk())

        and:
        await().atMost(5, TimeUnit.SECONDS).until {
            operationBatchAuditListener.getProcessedCount() >= 1
        }

        then:
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .get("/api/batches/history?trigger=CHARGEBACK&limit=10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath('$').isArray())
                .andExpect(jsonPath('$').isEmpty())
    }
}