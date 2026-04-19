Lab80
-----

Lab80--API-And-Security-Advanced--System-Logowania-i-BCrypt
===========================================================

Zrealizowaliśmy ogromną część planu. 
Twój system ma już "mięśnie" `asynchroniczność`, "mózg" (`CQRS` i analitykę) oraz "kłódkę" (`JWT` i role).
Skoro wszystko śmiga, a Ty przygotowujesz się na Mida, musimy dokończyć jeden krytyczny element z Etapu 2 (`Security`), 
który obecnie masz "udawany". 
W testach generujesz token ręcznie, ale w prawdziwej aplikacji użytkownik musi móc wysłać login/hasło i dostać ten token.

Przed Tobą Lab 81: 
`System Logowania` i `BCrypt` (Ostatni szlif bezpieczeństwa).

Cel:
Stworzenie endpointu POST `/api/auth/login`.

Implementacja weryfikacji hasła (użytkownik podaje admin123, a my sprawdzamy go z hashem $2a$10$... w bazie).

Wydanie tokenu JWT tylko po poprawnym zalogowaniu.

Krok-1. Upewnij się, że masz PasswordEncoder w SecurityConfig.groovy
--------------------------------------------------------------------
Spring Security musi wiedzieć, jak "rozszyfrować" (porównać) hasła.

```groovy
@Bean
org.springframework.security.crypto.password.PasswordEncoder passwordEncoder() {
return new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder()
}
```

Krok-2. Kontroler Autoryzacji (AuthController.groovy)
-----------------------------------------------------
Ten kontroler przyjmie login i hasło, sprawdzi je w bazie przez `CustomUserDetailsService` i jeśli są OK – wyda bilet (JWT).

`src/main/groovy/pl/edu/praktyki/web/AuthController.groovy`

```groovy
package pl.edu.praktyki.web

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException
import pl.edu.praktyki.security.JwtService
import pl.edu.praktyki.security.CustomUserDetailsService
import groovy.util.logging.Slf4j

@RestController
@RequestMapping("/api/auth")
@Slf4j
class AuthController {

    @Autowired CustomUserDetailsService userDetailsService
    @Autowired JwtService jwtService
    @Autowired PasswordEncoder passwordEncoder

    @PostMapping("/login")
    Map<String, String> login(@RequestBody Map<String, String> loginRequest) {
        String username = loginRequest.username
        String password = loginRequest.password

        log.info(">>> [AUTH] Próba logowania dla użytkownika: {}", username)

        // 1. Pobieramy użytkownika z bazy (przez nasz serwis)
        def userDetails = userDetailsService.loadUserByUsername(username)

        // 2. Sprawdzamy czy hasło pasuje do hasha
        if (!passwordEncoder.matches(password, userDetails.password)) {
            log.warn(">>> [AUTH] Błędne hasło dla użytkownika: {}", username)
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Błędne dane logowania")
        }

        // 3. Generujemy token (przekazujemy role wyciągnięte z userDetails)
        def roles = userDetails.authorities.collect { it.authority }
        String token = jwtService.generateToken(username, roles)

        return [
            username: username,
            token: token,
            type: "Bearer"
        ]
    }
}
```

Krok-3. Otwarcie drzwi dla logowania (SecurityConfig.groovy)
------------------------------------------------------------
Musimy pozwolić każdemu (nawet niezalogowanemu) wejść na adres /api/auth/login, bo inaczej nikt nigdy nie dostanie tokena!

Twój "system ochrony" (Spring Security) ma bramkarza, musisz mu dać silnik do sprawdzania dokumentów `AuthenticationManager`.
W najnowszym Spring Security 6 (Spring Boot 3), `AuthenticationManager` nie jest już automatycznie dostępny jako Bean. 
Jeśli chcesz go używać w swoim `AuthController`, musisz go jawnie "wystawić" w klasie konfiguracyjnej. 
Bez tego Spring widzi @Autowired i mówi: "Chcesz managera uwierzytelniania, ale ja nie wiem, skąd go wziąć!".
Musisz dodać jedną metodę (Bean) do swojego pliku `SecurityConfig.groovy`.


```groovy
// DODAJ TE IMPORTY:
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration
// ... reszta importów

.authorizeHttpRequests { auth ->
auth
.requestMatchers("/api/auth/**").permitAll() // <-- DODAJ TO
.requestMatchers("/swagger-ui/**", "/v3/api-docs/**", "/v3/api-docs", "/swagger-ui.html").permitAll()
.requestMatchers("/actuator/**").permitAll()
.anyRequest().authenticated()
}


// Adnotacja @Bean nad tą metodą mówi Springowi:
// "Gdy zobaczysz, że ktoś (np. AuthController) potrzebuje AuthenticationManager, uruchom tę funkcję i daj mu wynik".
// DODAJ TĘ METODĘ:
@Bean
AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
    // Ta metoda wyciąga gotowy silnik uwierzytelniania z konfiguracji Springa
    return config.getAuthenticationManager()
}
```
 

Krok-4. Test Spock – "Full Flow Security" (AuthSpec.groovy)
-----------------------------------------------------------
To jest test, który pokazuje rekruterowi, że rozumiesz cały proces.

Logujesz się poprawnym hasłem -> dostajesz token.

Używasz tego tokena, żeby wejść do chronionego `/api/transactions`.

Dodaj test (dodanie logowania):
`src/test/groovy/pl/edu/praktyki/web/AuthSpec.groovy`

```groovy
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
}

```

Krok-5: Uruchom test:
---------------------
Uruchom test `AuthSpec`.
`  ./gradlew test --tests "pl.edu.praktyki.web.AuthSpec" `
 

Podsumowanie:
-------------
Właśnie zaimplementowałeś Standardowy Workflow Bezpieczeństwa:
Dlaczego to jest poziom Mid?
Zamiast generować token "na boku" w teście (co jest oszustwem), sprawdzasz prawdziwy mechanizm:
Baza danych (Flyway V6).
Hashowanie haseł (BCrypt).
Protokół HTTP (Login -> Token -> Data).


Rejestracja: 
Przyjmujesz hasło, haszujesz (BCrypt) i zapisujesz do bazy (Postgres).

Logowanie: 
Spring Security `CustomUserDetailsService` wyciąga hasz z bazy, a `AuthenticationManager` porównuje go z tym, 
co wpisał użytkownik.

Wydanie biletu: 
Dopiero po przejściu tej weryfikacji wydajesz bilet (token) `JWT`.

Co dalej?
Etap 3: Zaawansowane JPA & Auditing (czyli system sam będzie wiedział, kto dodał którą transakcję i o której godzinie).