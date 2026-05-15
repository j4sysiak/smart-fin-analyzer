package pl.edu.praktyki.web

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.mock.web.MockMultipartFile
import org.springframework.test.web.servlet.MockMvc
import pl.edu.praktyki.BaseIntegrationSpec
import pl.edu.praktyki.security.JwtService
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@AutoConfigureMockMvc
class RbacSpec extends BaseIntegrationSpec {

    @Autowired MockMvc mvc
    @Autowired JwtService jwtService

    def "zwykły UŻYTKOWNIK nie powinien móc wgrywać plików (403 Forbidden)"() {
        given: "token dla zwykłego usera"
        def userToken = jwtService.generateToken("kowalski", ["ROLE_USER"])
        def csv = "id,amount,currency,category,description,date\nT1,500.00,PLN,Cat1,Desc1,2026-01-01".bytes
        def file = new MockMultipartFile("file", "test.csv", "text/csv", csv)
        // lub jeśli chcesz, możesz zostawić ten plik pusty, bo i tak powinien być odrzucany ze względu na uprawnienia:
        // def file = new MockMultipartFile("file", "test.csv", "text/csv", "data".bytes)

        when: "user próbuje zrobić upload"
        def response = mvc.perform(multipart("/api/transactions/upload")
                .file(file)
                .param("user", "kowalski")
                .header("Authorization", "Bearer $userToken"))
                .andDo(print())

        then: "zostaje odrzucony błędem 403"
        response.andExpect(status().isForbidden())
    }

    def "ADMINISTRATOR powinien móc wgrywać pliki (200 OK)"() {
        given: "token dla admina"
        def adminToken = jwtService.generateToken("boss", ["ROLE_ADMIN"])
        // Przygotuj poprawny CSV, żeby parser nie wybuchł
        def csv = "id,amount,currency,category,description,date\nT1,500.00,PLN,Cat1,Desc1,2026-01-01".bytes
        def file = new MockMultipartFile("file", "test.csv", "text/csv", csv)

        when: "admin robi upload"
        def response = mvc.perform(multipart("/api/transactions/upload")
                .file(file)
                .param("user", "boss")
                .header("Authorization", "Bearer $adminToken"))
                .andDo(print())

        then: "zostaje wpuszczony"
        response.andExpect(status().isOk())
    }

    def "zwykły UŻYTKOWNIK nie powinien mieć dostępu do globalnego total-summary (403 Forbidden)"() {
        given: "token dla zwykłego usera"
        def userToken = jwtService.generateToken("kowalski", ["ROLE_USER"])

        when: "user odpytuje globalny bilans systemu"
        def response = mvc.perform(get("/api/transactions/total-summary")
                .header("Authorization", "Bearer $userToken"))
                .andDo(print())

        then: "zostaje odrzucony błędem 403"
        response.andExpect(status().isForbidden())
    }

    def "ADMINISTRATOR powinien mieć dostęp do globalnego total-summary (200 OK)"() {
        given: "token dla admina"
        def adminToken = jwtService.generateToken("boss", ["ROLE_ADMIN"])

        when: "admin odpytuje globalny bilans systemu"
        def response = mvc.perform(get("/api/transactions/total-summary")
                .header("Authorization", "Bearer $adminToken"))
                .andDo(print())

        then: "otrzymuje poprawną odpowiedź 200"
        response.andExpect(status().isOk())
                .andExpect(jsonPath('$.globalTotalBalance').exists())
                .andExpect(jsonPath('$.syncTimestamp').exists())
    }
}