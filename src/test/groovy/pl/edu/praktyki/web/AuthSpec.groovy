package pl.edu.praktyki.web

import groovy.json.JsonSlurper
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import pl.edu.praktyki.BaseIntegrationSpec

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@AutoConfigureMockMvc
class AuthSpec extends BaseIntegrationSpec {

    @Autowired MockMvc mvc

    def "powinien zwrócić token JWT dla poprawnych danych logowania"() {
        given: "użytkownik admin (stworzony przez migrację V6, hasło: admin123)"
        def loginData = [username: "admin", password: "admin123"]
        def json = groovy.json.JsonOutput.toJson(loginData)

        when: "uderzamy w endpoint logowania"
        def response = mvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))

        then: "otrzymujemy 200 OK i token"
        response.andExpect(status().isOk())
                .andExpect(jsonPath('$.token').exists())
                .andExpect(jsonPath('$.username').value("admin"))
    }

    def "powinien odrzucić logowanie przy błędnym haśle"() {
        given: "admin z błędnym hasłem"
        def badLogin = [username: "admin", password: "ZLE_HASLO"]
        def json = groovy.json.JsonOutput.toJson(badLogin)

        when:
        def response = mvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))

        then: "status 401 Unauthorized"
        response.andExpect(status().isUnauthorized())
    }

    def "powinien przejść pełną ścieżkę od logowania do pobrania danych"() {
        given: "dane logowania (użytkownik admin jest w bazie dzięki migracji V6)"
        def loginJson = groovy.json.JsonOutput.toJson([username: "admin", password: "admin123"])

        when: "logujemy się"
        def loginResponse = mvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginJson))
                .andReturn().response.contentAsString

        def token = new JsonSlurper().parseText(loginResponse).token

        then: "otrzymaliśmy token"
        token != null

        when: "używamy otrzymanego tokena, aby pobrać transakcje"
        def dataResponse = mvc.perform(get("/api/transactions")
                .header("Authorization", "Bearer $token"))

        then: "zostajemy wpuszczeni (200 OK)"
        dataResponse.andExpect(status().isOk())
    }
}
