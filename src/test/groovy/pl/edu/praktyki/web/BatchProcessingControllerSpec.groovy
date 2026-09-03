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
import static com.github.tomakehurst.wiremock.client.WireMock.get
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@AutoConfigureMockMvc
@ActiveProfiles("tc")
@WithMockUser(username = "test-admin", roles = ["ADMIN"])
class BatchProcessingControllerSpec extends BaseIntegrationSpec {

    @Autowired
    MockMvc mvc

    @Autowired
    BankOperationClient bankOperationClient

    @Autowired
    OperationRepository operationRepository

    WireMockServer mockServer

    def setup() {
        operationRepository.deleteAll()

        mockServer = new WireMockServer(options().dynamicPort())
        mockServer.start()
        bankOperationClient.mockServerUrl = mockServer.baseUrl()

        // deposits -> 2 rekordy
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

        // withdrawals -> 1 rekord
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

        // transfers -> 1 rekord
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

        // conversions -> 2 rekordy (1 poprawny + 1 błędny bez fxRate)
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

    def "POST /api/batches/process bez operationType powinien uruchomić ALL i zwrócić summary"() {
        when:
        def response = mvc.perform(post("/api/batches/process")
                .with(csrf())
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
}