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

