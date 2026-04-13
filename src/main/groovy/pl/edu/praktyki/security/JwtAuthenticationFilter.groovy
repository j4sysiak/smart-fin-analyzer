package pl.edu.praktyki.security

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.filter.OncePerRequestFilter

// to jest nasz "filtr JWT", który będzie sprawdzał każdy przychodzący request pod kątem obecności i poprawności tokenu JWT.
// taki bramkarz, który przepuszcza dalej tylko te żądania, które mają ważny token JWT.
// Dzięki temu nasze kontrolery mogą być "nieświadome" mechanizmu JWT i po prostu sprawdzać,
// czy użytkownik jest zalogowany (SecurityContextHolder.getContext().getAuthentication() != null)
// i jakie ma uprawnienia.

class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService

    JwtAuthenticationFilter(JwtService jwtService) {
        this.jwtService = jwtService
    }

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
}