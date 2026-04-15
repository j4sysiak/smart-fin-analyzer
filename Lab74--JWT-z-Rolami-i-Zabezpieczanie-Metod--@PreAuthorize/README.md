Lab74
-----

Lab74--JWT-z-Rolami-i-Zabezpieczanie-Metod--@PreAuthorize
=========================================================

Weszliśmy na poziom, na którym Twoja aplikacja zaczyna przypominać prawdziwy system bankowy. 
Masz już "kłódkę" JWT, ale obecnie każdy, kto ma jakikolwiek poprawny token, może zrobić wszystko. 
W rzeczywistości użytkownik powinien widzieć tylko swoje statystyki, 
a tylko admin powinien móc wgrywać pliki CSV.

Kontynuujemy Etap 2 naszej mapy drogowej: `Role-Based Access Control (RBAC)`.

Cel:
Rozszerzenie tokena JWT o listę uprawnień `Claims`.
Zablokowanie endpointu upload dla zwykłych użytkowników.
Sprawdzenie tego "pancernymi" testami w Spocku.

Krok-1. Rozbudowa JwtService.groovy (Dodawanie ról do "biletu")
---------------------------------------------------------------
Musimy sprawić, aby nasz generator tokenów wpisywał do środka informację o roli (np. `ROLE_ADMIN`).

`src/main/groovy/pl/edu/praktyki/security/JwtService.groovy`

```groovy
// Zaktualizuj metodę generateToken, aby przyjmowała listę ról
    String generateToken(String username, List<String> roles = ["ROLE_USER"]) {
        return Jwts.builder()
                .subject(username)
                .claim("roles", roles) // <-- DODAJEMY ROLE DO TOKENA
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + EXPIRATION_TIME))
                .signWith(key)
                .compact()
    }

    // Dodaj metodę do wyciągania ról
    List<String> extractRoles(String token) {
        def claims = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload()
        
        return claims.get("roles", List.class) ?: []
    }
```
 
Krok-2. Aktualizacja Filtra (JwtAuthenticationFilter.groovy)
------------------------------------------------------------
Bramkarz musi teraz nie tylko sprawdzić dowód, ale też przeczytać, jakie uprawnienia ma gość, i przekazać je do Springa.

```groovy
    // Bramkarz musi nie tylko sprawdzić dowód,
// ale też przeczytać, jakie uprawnienia ma gość, i przekazać je do Springa.
@Override
protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) {
   String authHeader = request.getHeader("Authorization")

   // 1. Sprawdzamy czy nagłówek istnieje i zaczyna się od "Bearer "
   if (authHeader?.startsWith("Bearer ")) {
      String jwt = authHeader.substring(7)

      // 2. Weryfikujemy token
      if (jwtService.isTokenValid(jwt)) {
         String username = jwtService.extractUsername(jwt)
         // WYCIĄGAMY ROLE:
         def roles = jwtService.extractRoles(jwt)

         // Zmieniamy pustą listę [] na listę uprawnień Springa
         def authorities = roles
                 .collect { new org.springframework.security.core.authority.SimpleGrantedAuthority(it) }

         // 3. Tworzymy obiekt "zalogowanego użytkownika" w kontekście Springa
         def authToken = new UsernamePasswordAuthenticationToken(username, null, authorities)
         SecurityContextHolder.context.authentication = authToken
      }
   }
   // 4. Przekazujemy żądanie dalej (do kolejnych filtrów lub kontrolera)
   filterChain.doFilter(request, response)
}
```

Krok-3. Włączenie ochrony metod w SecurityConfig.groovy
-------------------------------------------------------
Musimy aktywować adnotację `@PreAuthorize`.

Dodaj nad klasą `SecurityConfig`:

```groovy
@Configuration
@EnableWebSecurity
@org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity // <-- TO WŁĄCZA OCHRONĘ METOD
class SecurityConfig { ... }
```

Krok-4. Zabezpieczenie Kontrolera (UploadController.groovy)
-----------------------------------------------------------
Teraz kładziemy blokadę na wgrywanie plików.

```groovy
@RestController
@RequestMapping("/api/transactions/upload")
class UploadController {

    @PostMapping
    @org.springframework.security.access.prepost.PreAuthorize("hasRole('ADMIN')") // <-- TYLKO ADMIN!
    ResponseEntity<String> uploadCsv(...) { ... }
}
```

Krok-5. Test Spock – "Weryfikacja Uprawnień" (RbacSpec.groovy)
--------------------------------------------------------------
To jest najważniejszy test dla Mida. Sprawdzamy dwa scenariusze:

 - User ma token, ale nie ma roli `ADMIN` -> dostaje `403`.
 - User ma token z rolą `ADMIN` -> zostaje wpuszczony.

`src/test/groovy/pl/edu/praktyki/web/RbacSpec.groovy`

```groovy
package pl.edu.praktyki.web

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.mock.web.MockMultipartFile
import org.springframework.test.web.servlet.MockMvc
import pl.edu.praktyki.BaseIntegrationSpec
import pl.edu.praktyki.security.JwtService
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@AutoConfigureMockMvc
class RbacSpec extends BaseIntegrationSpec {

    @Autowired MockMvc mvc
    @Autowired JwtService jwtService

    def "zwykły UŻYTKOWNIK nie powinien móc wgrywać plików (403 Forbidden)"() {
        given: "token dla zwykłego usera"
        def userToken = jwtService.generateToken("kowalski", ["ROLE_USER"])
        def file = new MockMultipartFile("file", "test.csv", "text/csv", "data".bytes)

        when: "user próbuje zrobić upload"
        def response = mvc.perform(multipart("/api/transactions/upload")
                .file(file)
                .param("user", "kowalski")
                .header("Authorization", "Bearer $userToken"))

        then: "zostaje odrzucony błędem 403"
        response.andExpect(status().isForbidden())
    }

    def "ADMINISTRATOR powinien móc wgrywać pliki (200 OK)"() {
        given: "token dla admina"
        def adminToken = jwtService.generateToken("boss", ["ROLE_ADMIN"])
        // Przygotuj poprawny CSV, żeby parser nie wybuchł
        def csv = "id,date,amount,currency,category,description\nT1,2026-01-01,10,PLN,X,Y".bytes
        def file = new MockMultipartFile("file", "test.csv", "text/csv", csv)

        when: "admin robi upload"
        def response = mvc.perform(multipart("/api/transactions/upload")
                .file(file)
                .param("user", "boss")
                .header("Authorization", "Bearer $adminToken"))

        then: "zostaje wpuszczony"
        response.andExpect(status().isOk())
    }
}
```

Dlaczego to jest "Enterprise Hardcore"?

Zrozumiałeś, że bezpieczeństwo ma dwie warstwy:

1. Authentication (Uwierzytelnienie): 
   Czy Twoje `JWT` jest prawdziwe? (To zrobił filtr).

2. Authorization (Autoryzacja): 
   Czy jako "User" masz prawo wejść do pokoju "Admina"? (To zrobiło `@PreAuthorize`).

Zadanie dla Ciebie:
Wdroż te 4 punkty. 
Jeśli test RbacSpec przejdzie, oznacza to, że Twój system finansowy jest gotowy na obsługę różnych typów użytkowników.

Daj znać, czy udało Ci się "odbić" zwykłego użytkownika od endpointu uploadu!