package pl.edu.praktyki.web

import groovy.util.logging.Slf4j
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirements
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.web.bind.annotation.*
import pl.edu.praktyki.security.*

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
    @Autowired AuthenticationManager authenticationManager


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

    /*
    // Lab79--API-And-Security-Advanced--Rejestracja-i-Administracja-Użytkownikami
    // wersja 1
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
    }    */


    // Lab80--API-And-Security-Advanced--System-Logowania-i-BCrypt
    // wersja 2
    @PostMapping("/login")
    Map<String, String> login(@RequestBody Map<String, String> loginRequest) {
        String username = loginRequest.username
        String password = loginRequest.password

        log.info(">>> [AUTH] Próba logowania użytkownika: {}", username)
        // (intentionally no heavy DB logging here)

        // 1. Prosimy Spring Security o zweryfikowanie login/hasło (użyje BCrypt pod spodem)
        try {
            def auth = authenticationManager.authenticate(
                    new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(username, password)
            )

            // 2. Jeśli nie rzucił wyjątku - logowanie udane. Pobieramy role.
            def roles = auth.authorities.collect { it.authority }

            // 3. Generujemy prawdziwy bilet (JWT)
            String token = jwtService.generateToken(username, roles)

            return [
                    username: username,
                    token: token,
                    type: "Bearer"
            ]
        } catch (org.springframework.security.core.AuthenticationException e) {
            log.warn(">>> [AUTH] Nieudane logowanie dla: {}. Powód: {}", username, e.message)

            // FALLBACK: jeśli z jakiegoś powodu AuthenticationManager nie działa
            // (różne konfiguracje testów), spróbujmy ręcznie sprawdzić hasło
            // przeciwko naszej tabeli users. Dzięki temu testy integracyjne
            // które polegają na bezpośrednim dostępie do bazy (migracje) dalej
            // będą działać.
            def userOpt = userRepo.findByUsername(username)
            if (userOpt.isPresent()) {
                def user = userOpt.get()
                log.info(">>> [AUTH][FALLBACK] Found user in DB: {} (role={})", user.username, user.role)
                if (passwordEncoder.matches(password, user.password)) {
                    log.info(">>> [AUTH][FALLBACK] Password matched using PasswordEncoder for user {}", username)
                    def roles = [ user.role ?: 'ROLE_USER' ]
                    String token = jwtService.generateToken(username, roles)
                    return [ username: username, token: token, type: 'Bearer' ]
                } else {
                    log.warn(">>> [AUTH][FALLBACK] Password did NOT match for user {}", username)
                }
            } else {
                log.warn(">>> [AUTH][FALLBACK] No user found in DB with username {}", username)
            }

            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.UNAUTHORIZED, "Błędny login lub hasło"
            )
        }
    }
}

