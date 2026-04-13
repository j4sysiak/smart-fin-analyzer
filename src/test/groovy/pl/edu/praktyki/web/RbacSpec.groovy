package pl.edu.praktyki.web

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.mock.web.MockMultipartFile
import org.springframework.test.web.servlet.MockMvc
import pl.edu.praktyki.BaseIntegrationSpec
import pl.edu.praktyki.security.JwtService
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart
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
                .andDo(org.springframework.test.web.servlet.result.MockMvcResultHandlers.print()) // <--- DODAJ TO, aby zobaczyć błąd w konsoli IntelliJ

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
                .andDo(org.springframework.test.web.servlet.result.MockMvcResultHandlers.print()) // <--- DODAJ TO, aby zobaczyć błąd w konsoli IntelliJ

        then: "zostaje wpuszczony"
        response.andExpect(status().isOk())
    }
}