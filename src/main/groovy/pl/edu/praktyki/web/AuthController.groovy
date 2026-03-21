package pl.edu.praktyki.web

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
    Map<String,String> token(@RequestParam(name = "user", required = false, defaultValue = "dev") String user) {
        String token = jwtService.generateToken(user)
        return [ token: token ]
    }
}

