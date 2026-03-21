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

        // ### 🔐 Architektura Bezpieczeństwa: Dlaczego wyłączono ochronę CSRF?

        //         W konfiguracji Spring Security celowo użyto polecenia `.csrf { it.disable() }`.
        //         W klasycznych aplikacjach webowych byłby to błąd bezpieczeństwa,
        //         jednak w przypadku naszego **REST API** jest to działanie w pełni poprawne i zalecane.

        //  **Uzasadnienie architektoniczne:**
        // 1. **Atak CSRF (Cross-Site Request Forgery)** opiera się na luce w przeglądarkach internetowych,
        //      które automatycznie dołączają ciasteczka sesyjne (Cookies) do każdego żądania wysyłanego do danej domeny.
        //      Złośliwa strona może to wykorzystać, zmuszając przeglądarkę nieświadomego użytkownika do wysłania ukrytego
        //      żądania (np. przelewu) z wykorzystaniem jego aktywnej sesji.
        // 2. **Bezstanowość (Statelessness):** Nasz system to bezstanowe REST API (`SessionCreationPolicy.STATELESS`).
        //      Nie polegamy na ciasteczkach (Cookies) ani na sesjach serwerowych (JSESSIONID) do uwierzytelniania użytkowników.
        // 3. **Autoryzacja przez Tokeny:** Komunikacja z API wymaga jawnego przekazania tokenu (np. JWT)
        //      w nagłówku HTTP (`Authorization: Bearer ...`).
        //      Przeglądarki internetowe **nie dołączają** niestandardowych nagłówków automatycznie w żądaniach
        //      z obcych stron (Cross-Origin).

        // **Wniosek:**
        // Ponieważ nie korzystamy z mechanizmu uwierzytelniania opartego na ciasteczkach,
        // wektor ataku CSRF nie ma punktu zaczepienia.
        // Włączenie domyślnej ochrony CSRF w Spring Security nie zwiększyłoby bezpieczeństwa systemu,
        // a jedynie utrudniło integrację z legalnymi klientami API (np. aplikacjami mobilnymi lub frontendem SPA),
        // które musiałyby niepotrzebnie obsługiwać tokeny CSRF.



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