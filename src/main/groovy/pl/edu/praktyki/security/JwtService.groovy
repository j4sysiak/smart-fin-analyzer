package pl.edu.praktyki.security

import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.springframework.stereotype.Service
import javax.crypto.SecretKey

@Service
class JwtService {

    // UWAGA: W prawdziwym projekcie ten klucz trzyma się w zmiennych środowiskowych (application.properties)!
    // Musi mieć minimum 256 bitów (32 znaki) dla algorytmu HS256.
    private final String SECRET_KEY = "ToJestBardzoTajnyKluczDoGenerowaniaTokenowJWT12345!"

    // Generujemy klucz kryptograficzny
    private final SecretKey key = Keys.hmacShaKeyFor(SECRET_KEY.getBytes())

    // Ważność tokena: np. 1 godzina
    private final long EXPIRATION_TIME = 1000 * 60 * 60

    /**
     * Drukuje "bilet wstępu" dla podanego użytkownika.
     */
    String generateToken(String username, List<String> roles = ["ROLE_USER"]) {
        return Jwts.builder()
                .subject(username) // Wrzucamy imię użytkownika do środka
                .claim("roles", roles) // <-- DODAJEMY ROLE DO TOKENA (Zaktualizuj metodę generateToken, aby przyjmowała listę ról)
                .issuedAt(new Date()) // Data wydania
                .expiration(new Date(System.currentTimeMillis() + EXPIRATION_TIME)) // Data ważności
                .signWith(key) // Podpisujemy naszym tajnym kluczem
                .compact() // Kompresujemy do postaci Stringa
    }

    // Metoda do wyciągania ról
    List<String> extractRoles(String token) {
        def claims = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload()

        return claims.get("roles", List.class) ?: []
    }

    /**
     * Wyciąga nazwę użytkownika z tokena.
     */
    String extractUsername(String token) {
        // Używamy jawnych wywołań getPayload() i getSubject(),
        // aby Groovy nie zgubił się w typach generycznych Javy
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .getSubject()
    }

    /**
     * Sprawdza, czy ktoś nie sfałszował tokena lub czy nie wygasł.
     */
    boolean isTokenValid(String token) {
        try {
            Jwts.parser().verifyWith(key).build().parseSignedClaims(token)
            return true
        } catch (Exception e) {
            println ">>> [JWT ERROR] Nieważny token: ${e.message}"
            return false
        }
    }
}