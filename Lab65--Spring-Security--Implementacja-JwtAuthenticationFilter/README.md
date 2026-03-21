Lab65
-----

Lab65--Spring-Security--Implementacja-JwtAuthenticationFilter
-------------------------------------------------------------

Teraz, kiedy mamy już generator i weryfikator tokenów `JwtService`, musimy "zamknąć bramę" naszego API.
Obecnie każdy endpoint w aplikacji wymaga autoryzacji (dzięki `SecurityConfig)`, 
ale nie mamy jeszcze mechanizmu, który weryfikowałby tokeny wysyłane w nagłówku Authorization: `Bearer <token>`.

W Spring Security, aby zweryfikować token przy każdym zapytaniu, używamy klasy `OncePerRequestFilter`. 
To jest "bramkarz", który sprawdza każdy przychodzący ruch.

Krok-1: Implementacja Filtru (`JwtAuthenticationFilter.groovy`)
---------------------------------------------------------------
Stwórz plik `src/main/groovy/pl/edu/praktyki/security/JwtAuthenticationFilter.groovy`:

```groovy
package pl.edu.praktyki.security

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.filter.OncePerRequestFilter

class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService

    JwtAuthenticationFilter(JwtService jwtService) {
        this.jwtService = jwtService
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) {
        String authHeader = request.getHeader("Authorization")

        // 1. Sprawdzamy czy nagłówek istnieje i zaczyna się od "Bearer "
        if (authHeader?.startsWith("Bearer ")) {
            String jwt = authHeader.substring(7)
            
            // 2. Weryfikujemy token
            if (jwtService.isTokenValid(jwt)) {
                String username = jwtService.extractUsername(jwt)
                
                // 3. Tworzymy obiekt "zalogowanego użytkownika" w kontekście Springa
                def authToken = new UsernamePasswordAuthenticationToken(username, null, [])
                SecurityContextHolder.context.authentication = authToken
            }
        }

        // 4. Przekazujemy żądanie dalej (do kolejnych filtrów lub kontrolera)
        filterChain.doFilter(request, response)
    }
}
```

Kork-2: Rejestracja Filtru w SecurityConfig
-------------------------------------------
Musimy teraz powiedzieć Springowi: "Hej, używaj mojego bramkarza przed sprawdzeniem uprawnień".
Zaktualizuj metodę `filterChain` w `SecurityConfig.groovy`:

```groovy
package pl.edu.praktyki.security

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain

@Configuration
@EnableWebSecurity
class SecurityConfig {

    @Autowired JwtService jwtService // Wstrzyknij serwis JWT

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http

        // ### 🔐 Architektura Bezpieczeństwa: Dlaczego wyłączono ochronę CSRF?

        //         W konfiguracji Spring Security celowo użyto polecenia `.csrf { it.disable() }`.
        //         W klasycznych aplikacjach webowych byłby to błąd bezpieczeństwa,
        //         jednak w przypadku naszego **REST API** jest to działanie w pełni poprawne i zalecane.

        //  **Uzasadnienie architektoniczne:**
        // 1. **Atak CSRF (Cross-Site Request Forgery)** opiera się na luce w przeglądarkach internetowych,
        //      które automatycznie dołączają ciasteczka sesyjne (Cookies) do każdego żądania wysyłanego do danej domeny.
        //      Złośliwa strona może to wykorzystać, zmuszając przeglądarkę nieświadomego użytkownika do wysłania ukrytego
        //      żądania (np. przelewu) z wykorzystaniem jego aktywnej sesji.
        // 2. **Bezstanowość (Statelessness):** Nasz system to bezstanowe REST API (`SessionCreationPolicy.STATELESS`).
        //      Nie polegamy na ciasteczkach (Cookies) ani na sesjach serwerowych (JSESSIONID) do uwierzytelniania użytkowników.
        // 3. **Autoryzacja przez Tokeny:** Komunikacja z API wymaga jawnego przekazania tokenu (np. JWT)
        //      w nagłówku HTTP (`Authorization: Bearer ...`).
        //      Przeglądarki internetowe **nie dołączają** niestandardowych nagłówków automatycznie w żądaniach
        //      z obcych stron (Cross-Origin).

        // **Wniosek:**
        // Ponieważ nie korzystamy z mechanizmu uwierzytelniania opartego na ciasteczkach,
        // wektor ataku CSRF nie ma punktu zaczepienia.
        // Włączenie domyślnej ochrony CSRF w Spring Security nie zwiększyłoby bezpieczeństwa systemu,
        // a jedynie utrudniło integrację z legalnymi klientami API (np. aplikacjami mobilnymi lub frontendem SPA),
        // które musiałyby niepotrzebnie obsługiwać tokeny CSRF.



        // 1. Wyłączamy CSRF (niepotrzebne w bezstanowym REST API)
                .csrf { it.disable() }

        // 2. Wyłączamy sesje (każde zapytanie musi mieć własny token)
                .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }

        // 3. Konfigurujemy uprawnienia do ścieżek
                .authorizeHttpRequests { auth ->
                    auth
                    // Pozwalamy wszystkim na dostęp do Swaggera (dokumentacji)
                            .requestMatchers("/swagger-ui/**", "/v3/api-docs/**", "/v3/api-docs", "/swagger-ui.html").permitAll()
                    // Pozwalamy na dostęp do Actuatora (Healthcheck)
                            .requestMatchers("/actuator/health").permitAll()
                    // WSZYSTKIE INNE ŚCIEŻKI (w tym nasze /api/transactions) WYMAGAJĄ ZALOGOWANIA
                            .anyRequest().authenticated()
                }

        // TUTAJ DODAJEMY NASZ FILTR przed standardowym UsernamePasswordAuthenticationFilter
                .addFilterBefore(new JwtAuthenticationFilter(jwtService),
                        org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter.class)

        return http.build()
    }
}
```

Jak to teraz przetestować (`Spock` + `MockMvc` + `JWT`)?
--------------------------------------------------------
Teraz Twoje testy `pl.edu.praktyki.web.TransactionControllerSpec` powinny zacząć rzucać błędy 403, 
bo nie wysyłasz tokenu! 
Musimy je zaktualizować, aby testy "miały bilet wstępu".

Dla przykładu i jasności, tworzę nowy test w `pl.edu.praktyki.web.TransactionControllerWithJWTSpec.groovy`,
dlatego, że chcemy mieć testy z `JWT`, a nie mieszać je z tymi bez `JWT` w `pl.edu.praktyki.web.TransactionControllerSpec.groovy`.
Nowy test będzie sprawdzał, czy poprawny token `JWT` pozwala na dostęp do endpointu.
Test ten jest analogiczny do tego, który mieliśmy wcześniej, ale teraz dodajemy nagłówek `Authorization` z tokenem JWT.

Nowy test w `pl.edu.praktyki.web.TransactionControllerWithJWTSpec.groovy`:

```groovy
package pl.edu.praktyki.web

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import pl.edu.praktyki.BaseIntegrationSpec
import pl.edu.praktyki.repository.TransactionEntity
import pl.edu.praktyki.repository.TransactionRepository
import pl.edu.praktyki.security.JwtService

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

// @ActiveProfiles("test")

// Spock-Spring 2.3 nie wykrywa @SpringBootTest jako triggera dla integracji Spring.
// Dodajemy @ContextConfiguration, aby Spock aktywował SpringExtension.
// @SpringBootTest zapewnia pełny kontekst Spring Boot z MockMvc.
// Zastosujmy "podwójny trigger": @SpringBootTest (dla Springa) i @ContextConfiguration (dla Spocka),
// żeby mieć 100% pewności, że kontekst wstanie.


// USUŃ @SpringBootTest, @ActiveProfiles, @ContextConfiguration
// Zostaw tylko @AutoConfigureMockMvc (bo jest specyficzne dla tego testu)
// @SpringBootTest(classes = [SmartFinDbApp])
// @ContextConfiguration
// @AutoConfigureMockMvc

// ZMIANA TUTAJ: Dodajemy @WithMockUser na całą klasę!
// @WithMockUser(username = "test-admin", roles = ["ADMIN"])
@AutoConfigureMockMvc
@ActiveProfiles(value = "tc", inheritProfiles = false)
class TransactionControllerWithJWTSpec extends BaseIntegrationSpec {

    // Wstrzykujemy JwtService, żeby wygenerować token w teście
    @Autowired JwtService jwtService

    @Autowired
    MockMvc mvc

    @Autowired
    TransactionRepository repository

    def setup() {
        // Przed każdym testem czyścimy bazę i dodajemy świeże dane
        repository.deleteAll()
        repository.save(new TransactionEntity(
                originalId: "T1", amountPLN: 100.0, category: "Test", description: "Wpływ"
        ))
        repository.save(new TransactionEntity(
                originalId: "T2", amountPLN: -20.0, category: "Jedzenie", description: "Pizza"
        ))
    }

    def "POST /api/transactions powinien przejść, gdy wyślemy poprawny token JWT"() {
        given: "ważny token JWT dla admina"
        String token = jwtService.generateToken("admin") // <-- KLUCZOWE JWT
        def payload = groovy.json.JsonOutput.toJson([id: "T1", amount: 100, category: "IT"])

        when: "wysyłamy żądanie z nagłówkiem Authorization"
        def response = mvc.perform(post("/api/transactions")
                .header("Authorization", "Bearer $token") // <-- KLUCZOWE JWT
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))

        then: "powinniśmy dostać 201 Created"
        response.andExpect(status().isCreated())
    }

    def "POST /api/transactions powinien zapisać transakcję, przeliczyć walutę i nadać tagi"() {
        given: "nowa transakcja w formacie JSON zbudowana za pomocą mapy Groovy"
        String token = jwtService.generateToken("admin") // <-- KLUCZOWE JWT
        // Zobacz jak czysto to wygląda! Żadnych klas, po prostu definicja danych.
        def newTxPayload =[
                id: "NEW-1",
                amount: -50,
                currency: "USD",
                category: "Gry",
                description: "Zakup na Steam"
        ]
        String jsonBody = groovy.json.JsonOutput.toJson(newTxPayload)

        when: "wysyłamy żądanie POST z nagłówkiem Authorization"
        def response = mvc.perform(post("/api/transactions")
                .header("Authorization", "Bearer $token") // <-- KLUCZOWE JWT
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonBody))

        then: "status HTTP to 201 Created"
        response.andExpect(status().isCreated())

        and: "waluta została przeliczona i zwrócona w odpowiedzi"
        // amountPLN nie wysyłaliśmy! API samo to policzyło. Sprawdzamy czy istnieje (nie jest null)
        response.andExpect(jsonPath('$.amountPLN').exists())

        and: "transakcja otrzymała tag (ponieważ -50 USD to więcej niż -100 PLN)"
        response.andExpect(jsonPath('$.tags[0]').value("BIG_SPENDER"))

        and: "baza danych powiększyła się o nowy rekord"
        repository.findAll().size() == 3 // 2 z metody setup() + 1 nowy
    }

    def "POST /api/transactions powinien odrzucić błędne dane i zwrócić 400 Bad Request"() {
        given: "JSON z brakującymi, wymaganymi polami (brak amount i category)"
        String token = jwtService.generateToken("admin") // <-- KLUCZOWE JWT
        def badPayload =[
                id: "BAD-1",
                currency: "EUR"
                // BRAK KATEGORII!
                // BRAK KWOTY!
        ]
        String jsonBody = groovy.json.JsonOutput.toJson(badPayload)

        when: "wysyłamy błędne żądanie POST z nagłówkiem Authorization"
        def response = mvc.perform(post("/api/transactions")
                .header("Authorization", "Bearer $token") // <-- KLUCZOWE JWT
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonBody))

        // Możesz odkomentować poniższą linię, aby zobaczyć piękny błąd w konsoli:
                .andDo(print())

        then: "odpowiedź to 400 Bad Request"
        response.andExpect(status().isBadRequest())

        and: "JSON z błędem zawiera szczegóły, czego brakuje"
        response.andExpect(jsonPath('$.status').value(400))
        response.andExpect(jsonPath('$.message').value(org.hamcrest.Matchers.containsString("amount: Kwota (amount) jest wymagana")))
        response.andExpect(jsonPath('$.message').value(org.hamcrest.Matchers.containsString("category: Kategoria jest wymagana")))
    }
}
```

Dlaczego to jest poziom Mid/Senior?
-----------------------------------
OncePerRequestFilter: 
Zrozumiałeś cykl życia żądania HTTP w Springu. 
Wiesz, że filtr działa zanim żądanie trafi do Twojego Kontrolera.

Stateless Security: 
Zaimplementowałeś architekturę, w której serwer nie pamięta żadnego użytkownika. 
Każde zapytanie musi "udowodnić" swoją tożsamość tokenem. 
To jest fundament skalowania aplikacji na tysiące użytkowników.

SecurityContextHolder: 
Poznałeś miejsce, w którym Spring trzyma "tożsamość" zalogowanego użytkownika przez cały czas trwania żądania.

Wyzwanie:
---------
Spróbuj wysłać żądanie w teście, używając starego (wygenerowanego chwilę wcześniej) tokena, 
ale z doklejonym znakiem X. 
Test powinien dostać 401 Unauthorized lub 403 Forbidden. Jeśli to przejdzie – Twoja bramka jest nie do sforsowania!


To jest kluczowy test bezpieczeństwa. 
W świecie cyberbezpieczeństwa nazywamy to "Tampering test" (test manipulacji). 
Sprawdzamy, czy jeśli ktoś podejmie próbę zmiany choćby jednego bitu w tokenie, nasz "bramkarz" (filtr) to wykryje.

Oto jak dopisać ten test do Twojego `SecurityControllerWithJWTSpec.groovy`.

```groovy
def "POST /api/transactions powinien odrzucić zapytanie ze sfałszowanym tokenem (403 Forbidden)"() {
given: "prawidłowy token"
String validToken = jwtService.generateToken("admin")

        and: "sfałszowany token (podmieniamy znak w środku)"
        // Zmieniamy znak na pozycji 10 (indeks 9)
        String fakeToken = validToken.substring(0, 9) + "X" + validToken.substring(10)
        
        def payload = groovy.json.JsonOutput.toJson([id: "T-HACK", amount: 100, category: "IT"])

        when: "wysyłamy żądanie z sfałszowanym tokenem"
        def response = mvc.perform(post("/api/transactions")
                .header("Authorization", "Bearer $fakeToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))

        then: "dostęp zostaje odrzucony (403 Forbidden)"
        // Spring Security w przypadku niepoprawnego tokena (błąd podpisu) 
        // zazwyczaj zwraca 403, bo uznaje próbę manipulacji za atak
        response.andExpect(status().isForbidden())
    }
```

Dlaczego to zadziała?
---------------------
Kryptografia JWT: 
Token JWT składa się z 3 części rozdzielonych kropką: 

     `Header.Payload.Signature` 

Podpis (Signature) jest generowany na podstawie Header, Payload oraz Twojego SECRET_KEY.

Błąd podpisu: 
Kiedy zmieniasz jakikolwiek znak w fakeToken, hash sygnatury przestaje się zgadzać z tym, co jest w części Signature.

JwtAuthenticationFilter: 
Twój filtr wywoła metodę jwtService.isTokenValid(fakeToken). 
Wewnątrz tej metody JJWT rzuci SignatureException (lub podobny), isTokenValid zwróci false, 
a Twój `JwtAuthenticationFilter` po prostu nie ustawi użytkownika w `SecurityContextHolder`.

SecurityChain: 
Spring Security widzi, że użytkownik nie jest zalogowany (isAuthenticated() zwraca false) i odrzuca żądanie z kodem 403.


