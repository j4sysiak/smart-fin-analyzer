package pl.edu.praktyki.web

import org.springframework.context.annotation.Profile
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.http.ResponseEntity
import org.springframework.http.HttpStatus
import groovy.util.logging.Slf4j
import org.springframework.security.core.context.SecurityContextHolder

/**
 * Tymczasowy endpoint diagnostyczny, aktywny tylko gdy profil != prod.
 * Zwraca informacje o aktualnym Authentication widzianym przez serwer.
 */
@RestController
@RequestMapping("/internal")
@Profile("!prod")
@Slf4j
class InternalDebugController {

    @GetMapping("/debug-auth")
    ResponseEntity<Map> debugAuth() {
        def auth = SecurityContextHolder.context?.authentication
        if (!auth) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body([authenticated: false, message: 'No Authentication in SecurityContext'])
        }

        def principal = auth.principal
        def authorities = []
        try {
            authorities = auth.authorities?.collect { it.authority }
        } catch (ignored) {
            // ignore if authorities not iterable
        }

        def body = [
                authenticated: auth.authenticated,
                name         : auth.name,
                principal    : principal instanceof String ? principal : principal?.toString(),
                authorities  : authorities
        ]

        log.info("/internal/debug-auth called - returning auth info: {}", body)
        return ResponseEntity.ok(body)
    }
}

