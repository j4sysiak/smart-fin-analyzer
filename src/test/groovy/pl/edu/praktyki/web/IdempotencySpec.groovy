package pl.edu.praktyki.web

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import pl.edu.praktyki.BaseIntegrationSpec
import pl.edu.praktyki.repository.TransactionRepository

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@AutoConfigureMockMvc
@ActiveProfiles("tc")
class IdempotencySpec extends BaseIntegrationSpec {

    @Autowired MockMvc mvc
    @Autowired TransactionRepository repo

    @WithMockUser(username = "user_idempotent", roles = ["USER"])
    def "powinien zapisac transakcje tylko raz przy podwójnym wysłaniu (idempotency)"() {
        given: "payload z tym samym business id"
        def payload = groovy.json.JsonOutput.toJson([
                id         : "UNIQUE-123",
                amount     : 100,
                currency   : "PLN",
                category   : "Test",
                description: "Idempotency check"
        ])

        when: "wysyłamy ten sam POST dwa razy"
        mvc.perform(post("/api/transactions")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
                .andExpect(status().isCreated())

        mvc.perform(post("/api/transactions")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
                .andExpect(status().isCreated())

        then: "w bazie jest tylko jeden rekord dla usera"
        repo.findAllByOwnerUsername(
                "user_idempotent",
                org.springframework.data.domain.Pageable.unpaged()
        ).totalElements == 1
    }
}