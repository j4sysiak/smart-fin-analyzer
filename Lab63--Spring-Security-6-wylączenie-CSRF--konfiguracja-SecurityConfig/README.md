Lab63
-----

Lab63--Spring-Security-6-wylączenie-CSRF--konfiguracja-SecurityConfig
---------------------------------------------------------------------

Bramkarz, czyli konfiguracja Spring Security 6
W najnowszym Spring Boot 3 (którego używamy) pod maską działa Spring Security 6. 
Cała konfiguracja opiera się teraz na tzw. `Lambda DSL` (żegnamy stare i brzydkie metody .and()).

Cel na dziś: 
Założymy "kłódkę" na nasz kontroler: `/api/transactions`, ale zostawimy otwartą furtkę dla dokumentacji Swagger, 
żebyśmy mogli podejrzeć, że kłódka faktycznie działa.

Krok 1: Dodanie zależności (build.gradle)
-----------------------------------------
Otwórz plik build.gradle i dodaj startera bezpieczeństwa oraz biblioteki do obsługi JWT (użyjemy nowoczesnej biblioteki jjwt).

```groovy
dependencies {
// ... Twoje poprzednie zależności ...

    // --- ETAP 2: SECURITY & JWT ---
    implementation 'org.springframework.boot:spring-boot-starter-security'
    testImplementation 'org.springframework.security:spring-security-test'
    
    // Biblioteki do generowania i weryfikacji tokenów JWT
    implementation 'io.jsonwebtoken:jjwt-api:0.12.5'
    runtimeOnly 'io.jsonwebtoken:jjwt-impl:0.12.5'
    runtimeOnly 'io.jsonwebtoken:jjwt-jackson:0.12.5'
}
```

Krok 2: Konfiguracja Bezpieczeństwa (SecurityConfig.groovy)
-----------------------------------------------------------

Opis co to takiego CSRF:
------------------------
Wyjaśnienie "po ludzku" (żebyś sam to kumał):

Wyobraź sobie, że logujesz się do swojego banku. Bank zostawia w Twojej przeglądarce ciasteczko (cookie). Dopóki masz to ciastko, bank wie, że Ty to Ty.
Teraz, nie zamykając banku, wchodzisz w nowej karcie na stronę ze śmiesznymi kotkami (którą stworzył haker). Pod obrazkiem kotka haker ukrył niewidoczny przycisk, który wysyła do Twojego banku polecenie: "Przelej 1000 zł na konto hakera".
I tu jest problem: Przeglądarka, wysyłając to polecenie w tle, z automatu doklei Twoje ciasteczko z banku. Bank spojrzy: "Aha, jest ciastko, to na pewno przelew od właściciela". Pieniądze znikają. To jest atak CSRF.

Dlaczego to nas nie obchodzi w naszym REST API?
Bo my wyłączyliśmy sesje i nie używamy ciasteczek! W naszej aplikacji użytkownik będzie musiał włożyć swój token (JWT) ręcznie do nagłówka zapytania (Authorization: Bearer token123). Haker ze strony z kotkami nie ma dostępu do tego nagłówka i nie zmusi przeglądarki, żeby go wysłała. Skoro nie używamy ciastek do logowania, atak CSRF jest fizycznie niemożliwy. Dlatego wyłączamy tę ochronę w Springu (csrf.disable()), żeby nam nie przeszkadzała.

Gotowy tekst do wklejenia do README.md
```text
### 🔐 Architektura Bezpieczeństwa: Dlaczego wyłączono ochronę CSRF?

W konfiguracji Spring Security celowo użyto polecenia `.csrf { it.disable() }`. W klasycznych aplikacjach webowych byłby to błąd bezpieczeństwa, jednak w przypadku naszego **REST API** jest to działanie w pełni poprawne i zalecane.

**Uzasadnienie architektoniczne:**
1. **Atak CSRF (Cross-Site Request Forgery)** opiera się na luce w przeglądarkach internetowych, które automatycznie dołączają ciasteczka sesyjne (Cookies) do każdego żądania wysyłanego do danej domeny. Złośliwa strona może to wykorzystać, zmuszając przeglądarkę nieświadomego użytkownika do wysłania ukrytego żądania (np. przelewu) z wykorzystaniem jego aktywnej sesji.
2. **Bezstanowość (Statelessness):** Nasz system to bezstanowe REST API (`SessionCreationPolicy.STATELESS`). Nie polegamy na ciasteczkach (Cookies) ani na sesjach serwerowych (JSESSIONID) do uwierzytelniania użytkowników.
3. **Autoryzacja przez Tokeny:** Komunikacja z API wymaga jawnego przekazania tokenu (np. JWT) w nagłówku HTTP (`Authorization: Bearer ...`). Przeglądarki internetowe **nie dołączają** niestandardowych nagłówków automatycznie w żądaniach z obcych stron (Cross-Origin).

**Wniosek:**
Ponieważ nie korzystamy z mechanizmu uwierzytelniania opartego na ciasteczkach, wektor ataku CSRF nie ma punktu zaczepienia. Włączenie domyślnej ochrony CSRF w Spring Security nie zwiększyłoby bezpieczeństwa systemu, a jedynie utrudniło integrację z legalnymi klientami API (np. aplikacjami mobilnymi lub frontendem SPA), które musiałyby niepotrzebnie obsługiwać tokeny CSRF.

```
 
Kontynuując,
  teraz stworzymy klasę, która zdefiniuje zasady dostępu do naszej aplikacji. 
Ponieważ budujemy `REST API` (a nie stronę WWW z logowaniem), wyłączymy sesje i zabezpieczenie `CSRF`.

Stwórz plik `src/main/groovy/pl/edu/praktyki/security/SecurityConfig.groovy`:

```groovy
package pl.edu.praktyki.security

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain

@Configuration
@EnableWebSecurity
class SecurityConfig {

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            // 1. Wyłączamy CSRF (niepotrzebne w bezstanowym REST API)
            .csrf { it.disable() }
            
            // 2. Wyłączamy sesje (każde zapytanie musi mieć własny token)
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            
            // 3. Konfigurujemy uprawnienia do ścieżek
            .authorizeHttpRequests { auth ->
                auth
                    // Pozwalamy wszystkim na dostęp do Swaggera (dokumentacji)
                    .requestMatchers("/swagger-ui/**", "/v3/api-docs/**", "/swagger-ui.html").permitAll()
                    // Pozwalamy na dostęp do Actuatora (Healthcheck)
                    .requestMatchers("/actuator/health").permitAll()
                    // WSZYSTKIE INNE ŚCIEŻKI (w tym nasze /api/transactions) WYMAGAJĄ ZALOGOWANIA
                    .anyRequest().authenticated()
            }
            
        return http.build()
    }
}
```

Krok 3: Udowodnienie, że kłódka działa (Test Spock)
---------------------------------------------------
Dodanie zależności Security sprawiło, że wszystkie Twoje testy kontrolerów `REST` (te z MockMvc) nagle przestały działać, 
bo odbijają się od ściany z kodem `401 Unauthorized`. 
Zobaczmy to w akcji!

Stwórzmy nowy (lub zaktualizujmy stary) test w `src/test/groovy/pl/edu/praktyki/web/SecurityControllerSpec.groovy`:

```groovy
package pl.edu.praktyki.web

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.test.web.servlet.MockMvc
import pl.edu.praktyki.BaseIntegrationSpec
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@AutoConfigureMockMvc
class SecurityControllerSpec extends BaseIntegrationSpec {

    @Autowired
    MockMvc mvc

  def "powinien odrzucić próbę pobrania transakcji bez autoryzacji (403 Forbidden)"() {
    expect: "niezalogowany użytkownik uderza w API i dostaje 403"
    mvc.perform(get("/api/transactions"))
    // ZAMIAST: .andExpect(status().isUnauthorized())
            .andExpect(status().isForbidden()) // <--- POPRAWKA TUTAJ
  }

    def "powinien wpuścić niezalogowanego użytkownika do Swaggera (PermitAll)"() {
        expect: "Swagger jest otwarty dla każdego"
        mvc.perform(get("/v3/api-docs"))
           .andExpect(status().isOk())
    }
}
```

Co się właśnie stało z Twoją aplikacją?

Zamknęliśmy frontowe drzwi: 
Od teraz żaden JSON nie wejdzie i nie wyjdzie z `/api/transactions` bez wylegitymowania się.

Bezstanowość (Stateless): 
Skonfigurowaliśmy Springa tak, aby "nie pamiętał" użytkowników między zapytaniami (brak sesji w ciasteczkach). 
To wymusza stosowanie tokenów.

Lambda DSL: 
Zastosowaliśmy nową składnię http.csrf { it.disable() }, która jest wymogiem na nowoczesnych projektach.

Twoje zadanie na dzisiaj:
-------------------------
1. Skonfiguruj build.gradle i klasę SecurityConfig.
2. Uruchom testy ./gradlew clean test.
3. Zobaczysz, że Twoje STARE testy kontrolerów (np. `TransactionControllerSpec`) padną, 
   zgłaszając 401 Unauthorized zamiast 200 OK lub 404 Not Found.
4. Uruchom nowy test SecurityControllerSpec, żeby potwierdzić, że blokada działa prawidłowo.



Podsumowując, w tym labie:
--------------------------
1. Założyliśmy na naszą aplikację Kłódkę
2. Wyłączyliśmy sesje (aplikacja jest bezstanowa)
3. Wyłączyliśmy CSRF (bo nie używamy ciasteczek)
4. Skonfigurowaliśmy SecurityConfig tak, by odrzucał każde zapytanie (403 Forbidden) do /api/transactions, jeśli klient nie ma "biletu wstępu".


