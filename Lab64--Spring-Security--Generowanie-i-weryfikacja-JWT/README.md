Lab64
-----

Lab64--Spring-Security--Generowanie-i-weryfikacja-JWT
-----------------------------------------------------

W Labie 63:
1. Założyliśmy na naszą aplikację Kłódkę
2. Wyłączyliśmy sesje (aplikacja jest bezstanowa)
3. Wyłączyliśmy CSRF (bo nie używamy ciasteczek)
4. Skonfigurowaliśmy SecurityConfig tak, by odrzucał każde zapytanie (403 Forbidden) do /api/transactions, jeśli klient nie ma "biletu wstępu".

Teraz musimy zbudować maszynę do drukowania tych biletów i bramkę, która je sprawdza. 
W nowoczesnych `REST API` tym biletem jest `JWT` (`JSON Web Token`).

Krok 1: Serwis do JWT (JwtService.groovy)
-----------------------------------------
Musimy napisać klasę, która umie "zaszyfrować" nazwę użytkownika w bezpieczny token oraz go później odczytać. 
W `build.gradle dodaliśmy już bibliotekę jjwt (wersja 0.12+).

Stwórz plik `src/main/groovy/pl/edu/praktyki/security/JwtService.groovy`:

```groovy
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
    String generateToken(String username) {
        return Jwts.builder()
                .subject(username) // Wrzucamy imię użytkownika do środka
                .issuedAt(new Date()) // Data wydania
                .expiration(new Date(System.currentTimeMillis() + EXPIRATION_TIME)) // Data ważności
                .signWith(key) // Podpisujemy naszym tajnym kluczem
                .compact() // Kompresujemy do postaci Stringa
    }

    /**
     * Wyciąga nazwę użytkownika z tokena.
     */
    String extractUsername(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .payload
                .subject
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
```

Krok 2: Superszybki Test Jednostkowy (JwtServiceSpec.groovy)
------------------------------------------------------------
Zanim podepniemy to pod Springa, upewnijmy się, że nasza kryptografia działa. 
Zauważ, że to czysty test jednostkowy (bez bazy, bez @SpringBootTest) – wykona się w ułamek sekundy!

Stwórz `src/test/groovy/pl/edu/praktyki/security/JwtServiceSpec.groovy`:

```groovy
package pl.edu.praktyki.security

import spock.lang.Specification

class JwtServiceSpec extends Specification {

    // Tworzymy serwis ręcznie
    def jwtService = new JwtService()

    def "powinien wygenerować i poprawnie zweryfikować token JWT"() {
        given: "nazwa użytkownika"
        def user = "admin"

        when: "generujemy token"
        def token = jwtService.generateToken(user)
        println ">>> Wygenerowany token JWT: $token"

        then: "token został stworzony"
        token != null
        token.startsWith("eyJ") // Każdy token JWT zaczyna się od eyJ

        and: "weryfikacja podpisem przechodzi pomyślnie"
        jwtService.isTokenValid(token) == true

        and: "możemy z niego odzyskać nazwę użytkownika"
        jwtService.extractUsername(token) == "admin"
    }

    def "powinien odrzucić sfałszowany token"() {
        given: "prawdziwy token z doklejoną literką (haker próbuje go zmienić)"
        def fakeToken = jwtService.generateToken("admin") + "a"

        expect:
        jwtService.isTokenValid(fakeToken) == false
    }
}
```

Czego właśnie się nauczyłeś? (The Mid-Level Way)

Kiedy mówisz na rozmowie kwalifikacyjnej: 
"Zabezpieczyłem API używając JWT", udowadniasz, że rozumiesz rozproszoną architekturę.
Dlaczego? 
Bo token JWT nie potrzebuje bazy danych do weryfikacji. 
Kiedy serwer go odbiera, po prostu sprawdza kryptograficzny podpis `verifyWith(key)`. 
Jeśli haker zmienił chociaż jedną literkę w tokenie, podpis przestanie się zgadzać, a metoda rzuci wyjątek (który u nas zwraca false). 
To sprawia, że API jest niewiarygodnie szybkie i skalowalne.

Twoje Zadanie:

Zaimplementuj JwtService i napisz test w Spocku.

Odpal sam test JwtServiceSpec i zobacz w konsoli, 
jak wygląda Twój nowiutki, pachnący kryptografią Token JWT (ten zaczynający się od eyJ...).

Gdy to zrobisz, będziemy mieli nasz "Bilet". W następnym kroku napiszemy Filtr, 
który stanie na bramce (przed Kontrolerem) i będzie sprawdzał ten bilet z każdego zapytania HTTP! 
Daj znać, jak poszło generowanie tokena! 🎟️🔐

