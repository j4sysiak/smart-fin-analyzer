Lab79
-----

Lab79--API-And-Security-Advanced--Rejestracja-i-Administracja-Użytkownikami
===========================================================================

Stworzenie 3 edpointów:
1. `/api/auth/register`, który pozwoli dodać nowego użytkownika.
2. `/api/auth/login`, zalogowanie usera i pobranie jego tokena JWT.
3. `/api/users`, który pozwoli odczytac wszystkich użytkowników z tabeli users.

Zastosujemy tutaj ważną zasadę: 
Nigdy nie zwracaj hasła (nawet hasha) w odpowiedzi API. 
Do tego posłuży nam wzorzec DTO (Data Transfer Object).

Krok 1: DTO dla Użytkownika (UserDto.groovy)
--------------------------------------------
Stworzymy prosty obiekt, który będzie bezpiecznie "podróżował" przez sieć, nie ujawniając wrażliwych danych.

`src/main/groovy/pl/edu/praktyki/security/UserDto.groovy`

```groovy
package pl.edu.praktyki.security

import groovy.transform.Canonical

@Canonical
class UserDto {
String username
String role
}
```

Krok 2: Rozbudowa AuthController.groovy (Rejestracja)
-----------------------------------------------------
Dodamy metodę `/register`. 
Zwróć uwagę na użycie `passwordEncoder.encode()` – to tutaj zamieniamy tekst na bezpieczny hash przed zapisem do bazy.

Dodaj do `pl.edu.praktyki.web.AuthController.groovy` (stworzonego wcześniej):

```groovy

package pl.edu.praktyki.web

import groovy.util.logging.Slf4j
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirements
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.web.bind.annotation.*
import org.springframework.beans.factory.annotation.Autowired
import pl.edu.praktyki.security.JwtService
import pl.edu.praktyki.security.UserDto
import pl.edu.praktyki.security.UserEntity
import pl.edu.praktyki.security.RegisterRequest
import pl.edu.praktyki.security.LoginRequest
import org.springframework.http.HttpStatus
import pl.edu.praktyki.security.UserRepository


/*
* Dev-only helper controller to quickly generate JWT tokens for local testing.
* Accessible without authentication and should NOT be exposed in production.
*/

@RestController
@RequestMapping("/api/auth") // <--- ŚCIEŻKA BAZOWA
@Slf4j
class AuthController {

    @Autowired JwtService jwtService
    @Autowired UserRepository userRepo
    @Autowired PasswordEncoder passwordEncoder

    /**
     * GET /api/auth/token?user=admin
     * Returns a JSON object with a Bearer token for the requested username.
     */
    @GetMapping("/token") // <--- ŚCIEŻKA METODY
    @Operation(summary = "Pobierz token JWT (publiczny endpoint)",
            description = "Zwraca token JWT dla podanego użytkownika. Nie wymaga autoryzacji. Skopiuj wartość 'token' z odpowiedzi, kliknij 🔒 Authorize i wklej ją tam.")
    @SecurityRequirements  // <-- pusty = oznacza Swaggerowi, że TEN endpoint NIE wymaga tokena
    Map<String,String> token(@RequestParam(name = "user", required = false, defaultValue = "dev") String user) {
        // Jeśli prosisz o token dla 'admin' - nadajemy rolę ADMIN, w pozostałych przypadkach domyślnie ROLE_USER
        def roles = (user == 'admin') ? ['ROLE_ADMIN'] : ['ROLE_USER']
        String token = jwtService.generateToken(user, roles)
        return [ token: token ]
    }

    // Przykładowy endpoint rejestracji, który hash'uje hasło przed zapisem do bazy.
    // /api/auth/register, który pozwoli dodać nowego użytkownika.
    @PostMapping("/register") // <--- ŚCIEŻKA METODY
    @ResponseStatus(HttpStatus.CREATED)
    UserDto register(@RequestBody RegisterRequest request) {
        // Przyjmujemy typowane DTO zamiast Mapy, dzięki czemu Swagger/OpenAPI
        // poprawnie wyświetli schema (username, password, role) zamiast
        // dodatkowychProp1/2/3.
        String username = request.username
        String rawPassword = request.password
        String role = request.role ?: "ROLE_USER" // Domyślnie zwykły user
        // Normalizuj rolę: upewnij się, że zaczyna się od "ROLE_"
        if (!role?.startsWith('ROLE_')) {
            role = "ROLE_${role}"
        }

        if (userRepo.findByUsername(username).isPresent()) {
            throw new org.springframework.web.server.ResponseStatusException(
                    HttpStatus.CONFLICT, "Użytkownik o nazwie $username już istnieje"
            )
        }

        // 1. Hashujemy hasło przed zapisem!
        String hashedPassword = passwordEncoder.encode(rawPassword)

        // 2. Tworzymy i zapisujemy encję
        def newUser = new UserEntity(
                username: username,
                password: hashedPassword,
                role: role
        )
        userRepo.save(newUser)

        log.info(">>> [AUTH] Zarejestrowano nowego użytkownika: {}", username)

        // 3. Zwracamy bezpieczne DTO
        return new UserDto(username: username, role: role)
    }


    @PostMapping("/login")
    @ResponseStatus(HttpStatus.OK)
    @Operation(summary = "Zaloguj użytkownika i otrzymaj token",
            description = "Weryfikuje nazwę użytkownika i hasło; zwraca token JWT jeśli dane są poprawne.")
    @SecurityRequirements // public
    Map<String, String> login(@RequestBody LoginRequest request) {
        String username = request.username
        String rawPassword = request.password

        def userOpt = userRepo.findByUsername(username)
        if (!userOpt.isPresent()) {
            throw new org.springframework.web.server.ResponseStatusException(HttpStatus.UNAUTHORIZED, "Nieprawidłowe dane logowania")
        }

        def user = userOpt.get()
        if (!passwordEncoder.matches(rawPassword, user.password)) {
            throw new org.springframework.web.server.ResponseStatusException(HttpStatus.UNAUTHORIZED, "Nieprawidłowe dane logowania")
        }

        // Upewnij się, że token zawiera role z prefixem ROLE_
        def rawRole = user.role ?: 'ROLE_USER'
        def normalized = rawRole?.startsWith('ROLE_') ? rawRole : "ROLE_${rawRole}"
        def roles = [ normalized ]
        String token = jwtService.generateToken(username, roles)
        return [ token: token ]
    }
}


```

Krok 3: Endpoint Administracyjny (UserController.groovy)
--------------------------------------------------------
Zgodnie z Twoją prośbą, stworzymy listę wszystkich użytkowników. 
Zabezpieczymy ją tak, aby tylko ADMIN mógł ją zobaczyć.

Stwórz `src/main/groovy/pl/edu/praktyki/web/UserController.groovy`

```groovy
package pl.edu.praktyki.web

import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import pl.edu.praktyki.security.UserDto
import pl.edu.praktyki.security.UserRepository

@RestController
@RequestMapping("/api/users")
@Slf4j
class UserController {

    @Autowired UserRepository userRepo

    // Endpoint do pobierania listy wszystkich użytkowników. Dostępny tylko dla adminów.
    // Endpoint: /api/users, który pozwoli odczytać wszystkich użytkowników
    // Zwraca listę obiektów UserDto (zawierających tylko username i role, bez hasła).
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')") // <-- Tylko admin może pobrać listę (w bazie w tabeli users musi mieć rolę `ROLE_ADMIN`)
    List<UserDto> getAllUsers() {
        return userRepo.findAll().collect { user  ->
            new UserDto(username: user.username, role: user.role)
        }
    }
}
```

Krok 4: Aktualizacja SecurityConfig.groovy
------------------------------------------
Musimy udostępnić rejestrację dla wszystkich (inaczej nikt nie mógłby założyć konta).

```groovy
                .authorizeHttpRequests { auth ->
    // Jeśli jesteśmy poza produkcją, udostępniamy endpointy diagnostyczne /internal/** bez autoryzacji
    if (!env.acceptsProfiles(Profiles.of('prod'))) {
        auth.requestMatchers('/internal/**').permitAll()
    }
    // Pozwalamy wszystkim na dostęp do Swaggera i Dokumentacji
    auth.requestMatchers('/swagger-ui/**').permitAll()
    auth.requestMatchers('/v3/api-docs/**').permitAll()
    auth.requestMatchers('/v3/api-docs').permitAll()
    auth.requestMatchers('/swagger-ui.html').permitAll()

    // Pozwalamy na dostęp do Actuatora (Healthcheck)
    auth.requestMatchers('/actuator/**').permitAll()

    // Pozwalamy na dostęp do H2 Web Console (tylko w trybie lokalnym / testowym)
    auth.requestMatchers('/h2-console', '/h2-console/**').permitAll()

    // Allow unauthenticated access to dev auth token endpoint
    auth.requestMatchers('/api/auth/token').permitAll()

    // Allow unauthenticated access to dev register user endpoint and login endpoint
    auth.requestMatchers('/api/auth/register').permitAll()
    auth.requestMatchers("/api/auth/login").permitAll()

    // NOTE: Do NOT make /api/users public if method-level security (@PreAuthorize)
    // is used to protect it. Keep it authenticated so that @PreAuthorize can work.
    // If you want GET /api/users to be admin-only, do NOT call permitAll() here.
    // auth.requestMatchers('/api/users').permitAll()

    // WSZYSTKIE INNE ŚCIEŻKI (w tym nasze /api/transactions) WYMAGAJĄ ZALOGOWANIA
    auth.anyRequest().authenticated()
}
```

Krok 5: Test Spock (UserManagementSpec.groovy)
----------------------------------------------
Sprawdźmy cały proces: od rejestracji po odczyt.

`src/test/groovy/pl/edu/praktyki/web/UserManagementSpec.groovy`

```groovy
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
```

Dlaczego to jest ważne dla Mida?

Zabezpieczenie przed wyciekiem (DTO): 
Nigdy nie przesyłaj encji bazy danych bezpośrednio do JSON-a. 
Pokazujesz, że o tym pamiętasz.

Hashowanie w Locie: 
Pokazujesz, że rozumiesz, iż hasło z formularza musi zostać "przemielone" przez `BCrypt` natychmiast.

Role-Based Security: 
Twój test weryfikuje, że `ROLE_USER` nie wejdzie tam, gdzie `ROLE_ADMIN`.

Zadanie:
--------
Wdroż DTO i kontrolery.
Uruchom test `UserManagementSpec`.
Jeśli test przejdzie, spróbuj zarejestrować kogoś przez Postmana i sprawdź DBeaverem, 
czy hasło w bazie jest nieczytelne.