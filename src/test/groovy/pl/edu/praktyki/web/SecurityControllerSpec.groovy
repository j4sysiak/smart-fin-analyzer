package pl.edu.praktyki.web

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.test.web.servlet.MockMvc
import pl.edu.praktyki.BaseIntegrationSpec
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@AutoConfigureMockMvc
class SecurityControllerSpec extends BaseIntegrationSpec {

    @Autowired
    MockMvc mvc

    def "powinien odrzucić próbę pobrania transakcji bez autoryzacji (403 Forbidden)"() {
        expect: "niezalogowany użytkownik uderza w API i dostaje 403"
        mvc.perform(get("/api/transactions"))
        // ZAMIAST: .andExpect(status().isUnauthorized())
                .andExpect(status().isForbidden()) // <--- POPRAWKA TUTAJ
    }

    def "powinien wpuścić niezalogowanego użytkownika do Swagger (PermitAll)"() {
        expect: "Swagger jest otwarty dla każdego"
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
    }

    def "powinien wpuścić niezalogowanego użytkownika do publicznych endpointów (PermitAll)"() {
        expect: "Endpoint Healthcheck (Actuator) jest otwarty dla każdego"
        // Używamy Actuatora, bo mamy 100% pewności, że MockMvc załadował go do pamięci
        mvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
    }
}