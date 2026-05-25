package pl.edu.praktyki.web

import groovy.json.JsonOutput
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.cache.CacheManager
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import pl.edu.praktyki.BaseIntegrationSpec
import spock.lang.Unroll

import static org.springframework.http.MediaType.APPLICATION_JSON
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@AutoConfigureMockMvc
@WithMockUser(username = "test-admin", roles = ["ADMIN"])
@TestPropertySource(properties = [
        "banking.rules.amount-threshold=10000"
])
class TransactionAnalysisControllerSpec extends BaseIntegrationSpec {

    @Autowired
    MockMvc mvc

    @Autowired
    CacheManager cacheManager

    def setup() {
        cacheManager.getCache("transactionAnalysis")?.clear()
    }

    @Unroll
    def "POST /api/transactions/analyze zwraca #expectedDecision dla amount=#amount"() {
        given:
        def payload = [
                transactionId: transactionId,
                accountId    : "ACC-REST-001",
                correlationId: correlationId,
                timestamp    : "2026-05-23T16:00:00Z",
                amount       : amount,
                payload      : [:]
        ]
        String body = JsonOutput.toJson(payload)

        expect:
        mvc.perform(post("/api/transactions/analyze")
                .with(csrf())
                .contentType(APPLICATION_JSON)
                .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath('$.transactionId').value(transactionId))
                .andExpect(jsonPath('$.correlationId').value(correlationId))
                .andExpect(jsonPath('$.decision').value(expectedDecision))
                .andExpect(jsonPath('$.reason').value(expectedReason))
                .andExpect(jsonPath('$.decidedAt').exists())

        where:
        transactionId | correlationId    | amount   || expectedDecision       | expectedReason
        "TX-REST-001" | "CORR-REST-001" | 9999.99  || "ACCEPT"              | "Status OK - transaction accepted"
        "TX-REST-002" | "CORR-REST-002" | 10000.01 || "ACCEPT_WITH_WARNING" | "Transaction flagged - manual review required"
        "TX-REST-003" | "CORR-REST-003" | null     || "REJECT"              | "Processing failed - transaction rejected"
    }

    def "POST /api/transactions/analyze zwraca 400 gdy transactionId jest pusty"() {
        given:
        def payload = [
                transactionId: "",
                accountId    : "ACC-REST-001",
                correlationId: "CORR-REST-001",
                timestamp    : "2026-05-23T16:00:00Z",
                amount       : 100.00,
                payload      : [:]
        ]
        String body = JsonOutput.toJson(payload)

        expect:
        mvc.perform(post("/api/transactions/analyze")
                .with(csrf())
                .contentType(APPLICATION_JSON)
                .content(body))
                .andExpect(status().isBadRequest())
    }

    def "POST /api/transactions/analyze zwraca 400 gdy timestamp jest null"() {
        given:
        def payload = [
                transactionId: "TX-REST-002",
                accountId    : "ACC-REST-002",
                correlationId: "CORR-REST-002",
                // NO timestamp!
                amount       : 100.00,
                payload      : [:]
        ]
        String body = JsonOutput.toJson(payload)

        expect:
        mvc.perform(post("/api/transactions/analyze")
                .with(csrf())
                .contentType(APPLICATION_JSON)
                .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath('$.status').value(400))
                .andExpect(jsonPath('$.message').value(org.hamcrest.Matchers.containsString("timestamp")))
    }

    def "POST /api/transactions/analyze zwraca 400 gdy correlationId jest pusty"() {
        given:
        def payload = [
                transactionId: "TX-REST-003",
                accountId    : "ACC-REST-003",
                correlationId: "",  // <- pusty!
                timestamp    : "2026-05-23T16:00:00Z",
                amount       : 100.00,
                payload      : [:]
        ]
        String body = JsonOutput.toJson(payload)

        expect:
        mvc.perform(post("/api/transactions/analyze")
                .with(csrf())
                .contentType(APPLICATION_JSON)
                .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath('$.status').value(400))
                .andExpect(jsonPath('$.message').value(org.hamcrest.Matchers.containsString("correlationId")))
    }
}