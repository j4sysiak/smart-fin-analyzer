package pl.edu.praktyki.web

import pl.edu.praktyki.BaseIntegrationSpec
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.security.test.context.support.WithMockUser
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath

@AutoConfigureMockMvc
class UserManagementSpec extends BaseIntegrationSpec {

    @Autowired MockMvc mvc

    def "powinien zarejestrować nowego użytkownika"() {
        given: "dane nowego użytkownika"
        def newUser =[username: "nowy_user", password: "tajnePassword123"]
        def json = groovy.json.JsonOutput.toJson(newUser)

        when: "wywołujemy rejestrację"
        def response = mvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))

        then: "zwraca 201 Created i poprawne dane bez hasła"
        response.andExpect(status().isCreated())
                .andExpect(jsonPath('$.username').value("nowy_user"))
                .andExpect(jsonPath('$.password').doesNotExist()) // HASŁO NIE MOŻE WYCIEKNĄĆ!
    }

    @WithMockUser(roles = ["ADMIN"])
    def "ADMIN powinien móc pobrać listę wszystkich użytkowników"() {
        expect:
        mvc.perform(get("/api/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath('$').isArray())
    }

    @WithMockUser(roles = ["USER"])
    def "zwykły UŻYTKOWNIK nie powinien mieć dostępu do listy użytkowników"() {
        expect:
        mvc.perform(get("/api/users"))
                .andExpect(status().isForbidden())
    }
}