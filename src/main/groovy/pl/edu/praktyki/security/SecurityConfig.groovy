package pl.edu.praktyki.security

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain
import jakarta.servlet.Filter
import jakarta.servlet.FilterChain
import jakarta.servlet.ServletException
import jakarta.servlet.ServletRequest
import jakarta.servlet.ServletResponse
import jakarta.servlet.FilterConfig
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse

@Configuration
@EnableWebSecurity
class SecurityConfig {

    @Autowired JwtService jwtService // Wstrzyknij serwis JWT

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
                            .requestMatchers("/swagger-ui/**", "/v3/api-docs/**", "/v3/api-docs", "/swagger-ui.html").permitAll()
                    // Pozwalamy na dostęp do Actuatora (Healthcheck)
                            //.requestMatchers("/actuator/health").permitAll()
                            .requestMatchers("/actuator/**").permitAll()
                    // Pozwalamy na dostęp do H2 Web Console (tylko w trybie lokalnym / testowym)
                            .requestMatchers("/h2-console", "/h2-console/**").permitAll()
                    // Allow unauthenticated access to dev auth token endpoint
                            .requestMatchers("/auth/**").permitAll()
                    // WSZYSTKIE INNE ŚCIEŻKI (w tym nasze /api/transactions) WYMAGAJĄ ZALOGOWANIA
                            .anyRequest().authenticated()
                }

                // H2 Console uses frames - pozwalamy na wyświetlanie w tej samej domenie
                .headers { it.frameOptions { it.sameOrigin() } }

                // ZAMIENIAMY BEZPOŚREDNIE DODANIE FILTRA JWT NA FILTR WARUNKOWY
                // Filtr warunkowy pominie filtrowanie JWT dla ścieżek publicznych (m.in. H2 console)
                .addFilterBefore(new ConditionalJwtAuthenticationFilter(jwtService),
                        org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter.class)

        return http.build()
    }

    // Warunkowy filtr: tylko dla nie-publicznych ścieżek deleguje do właściwego JwtAuthenticationFilter
    class ConditionalJwtAuthenticationFilter implements Filter {
        JwtService jwtService

        ConditionalJwtAuthenticationFilter(JwtService jwtService) {
            this.jwtService = jwtService
        }

        @Override
        void init(FilterConfig filterConfig) throws ServletException {
            // no-op
        }

        @Override
        void destroy() {
            // no-op
        }

        @Override
        void doFilter(ServletRequest req, ServletResponse res, FilterChain chain) throws IOException, ServletException {
            HttpServletRequest request = (HttpServletRequest) req
            HttpServletResponse response = (HttpServletResponse) res
            String path = request.getRequestURI()

            // Lista ścieżek publicznych, które nie wymagają walidacji JWT
            // Używamy contains() zamiast startsWith() - bardziej odporne na kontekst aplikacji
            if (path.contains('/swagger-ui') ||
                    path.contains('/v3/api-docs') ||
                    path.contains('/actuator/health') ||
                    path.contains('/h2-console') ||
                    path.contains('/auth/')) {
                chain.doFilter(request, response)
                return
            }

            // Dla pozostałych ścieżek delegujemy do istniejącego filtra JWT
            // Zakładamy, że klasa JwtAuthenticationFilter istnieje i obsługuje proces uwierzytelnienia
            new JwtAuthenticationFilter(jwtService).doFilter(request, response, chain)
        }
    }
}