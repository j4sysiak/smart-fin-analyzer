package pl.edu.praktyki.security

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.env.Environment
import org.springframework.core.env.Profiles
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
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration

@Configuration
@EnableWebSecurity
@org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity // <-- TO WŁĄCZA OCHRONĘ METOD
class SecurityConfig {

    @Autowired JwtService jwtService // Wstrzyknij serwis JWT
    @Autowired Environment env

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
                    // Jeśli jesteśmy poza produkcją, udostępniamy endpointy diagnostyczne /internal/** bez autoryzacji
                    if (!env.acceptsProfiles(Profiles.of('prod'))) {
                        auth.requestMatchers('/internal/**').permitAll()
                    }
                    // Pozwalamy wszystkim na dostęp do Swaggera i Dokumentacji
                    auth.requestMatchers('/swagger-ui/**').permitAll()
                    auth.requestMatchers('/v3/api-docs/**').permitAll()
                    auth.requestMatchers('/v3/api-docs').permitAll()
                    auth.requestMatchers('/swagger-ui.html').permitAll()

                    // Pozwalamy na dostęp do Actuatora (Healthcheck)
                    auth.requestMatchers('/actuator/**').permitAll()

                    // Pozwalamy na dostęp do H2 Web Console (tylko w trybie lokalnym / testowym)
                    auth.requestMatchers('/h2-console', '/h2-console/**').permitAll()

                    // Allow unauthenticated access to dev auth token endpoint
                    auth.requestMatchers('/api/auth/token').permitAll()

                    // Allow unauthenticated access to dev register user endpoint and login endpoint
                    auth.requestMatchers('/api/auth/register').permitAll()
                    auth.requestMatchers("/api/auth/login").permitAll()

                    // NOTE: Do NOT make /api/users public if method-level security (@PreAuthorize)
                    // is used to protect it. Keep it authenticated so that @PreAuthorize can work.
                    // If you want GET /api/users to be admin-only, do NOT call permitAll() here.
                    // auth.requestMatchers('/api/users').permitAll()

                    // WSZYSTKIE INNE ŚCIEŻKI (w tym nasze /api/transactions) WYMAGAJĄ ZALOGOWANIA
                    auth.anyRequest().authenticated()
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
            // Dopasowujemy tę listę do reguł w authorizeHttpRequests (ważne — jeśli endpoint jest publiczny
            // to filtr warunkowy nie powinien wymagać tokena, inaczej permitAll() nie będzie miało znaczenia).
            // Używamy contains() zamiast startsWith() — prostsze dopasowanie w kontekście Groovy/URI.
            if (path.contains('/swagger-ui') ||
                    path.contains('/v3/api-docs') ||
                    path.contains('/actuator') ||
                    path.contains('/h2-console') ||
                    path.contains('/api/auth') ||
                    path.contains('/internal/')) {
                chain.doFilter(request, response)
                return
            }

            // Dla pozostałych ścieżek delegujemy do istniejącego filtra JWT
            // Zakładamy, że klasa JwtAuthenticationFilter istnieje i obsługuje proces uwierzytelnienia
            new JwtAuthenticationFilter(jwtService).doFilter(request, response, chain)
        }
    }

    // Adnotacja @Bean nad tą metodą mówi Springowi: "Gdy zobaczysz, że ktoś (np. AuthController)
    // potrzebuje PasswordEncoder, uruchom tę funkcję i daj mu wynik".
    @Bean
    org.springframework.security.crypto.password.PasswordEncoder passwordEncoder() {
        return new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder()
    }

    // Adnotacja @Bean nad tą metodą mówi Springowi: "Gdy zobaczysz, że ktoś (np. AuthController)
    // potrzebuje AuthenticationManager, uruchom tę funkcję i daj mu wynik".
    @Bean
    AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        // Ta metoda wyciąga gotowy silnik uwierzytelniania z konfiguracji Springa
        return config.getAuthenticationManager()
    }

}