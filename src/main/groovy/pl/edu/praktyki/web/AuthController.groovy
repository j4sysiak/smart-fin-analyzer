package pl.edu.praktyki.web

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirements
import org.springframework.web.bind.annotation.*
import org.springframework.beans.factory.annotation.Autowired
import pl.edu.praktyki.security.JwtService

/**
 * Dev-only helper controller to quickly generate JWT tokens for local testing.
 * Accessible without authentication and should NOT be exposed in production.
 */
@RestController
@RequestMapping("/auth")
class AuthController {

    @Autowired JwtService jwtService

    /**
     * GET /auth/token?user=admin
     * Returns a JSON object with a Bearer token for the requested username.
     */
    @GetMapping("/token")
    @Operation(summary = "Pobierz token JWT (publiczny endpoint)",
            description = "Zwraca token JWT dla podanego użytkownika. Nie wymaga autoryzacji. Skopiuj wartość 'token' z odpowiedzi, kliknij 🔒 Authorize i wklej ją tam.")
    @SecurityRequirements  // <-- pusty = oznacza Swaggerowi, że TEN endpoint NIE wymaga tokena
    Map<String,String> token(@RequestParam(name = "user", required = false, defaultValue = "dev") String user) {
        // Jeśli prosisz o token dla 'admin' - nadajemy rolę ADMIN, w pozostałych przypadkach domyślnie ROLE_USER
        def roles = (user == 'admin') ? ['ROLE_ADMIN'] : ['ROLE_USER']
        String token = jwtService.generateToken(user, roles)
        return [ token: token ]
    }
}

