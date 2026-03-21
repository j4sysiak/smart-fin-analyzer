package pl.edu.praktyki.security

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.filter.OncePerRequestFilter

class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService

    JwtAuthenticationFilter(JwtService jwtService) {
        this.jwtService = jwtService
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) {
        String authHeader = request.getHeader("Authorization")

        // 1. Sprawdzamy czy nagłówek istnieje i zaczyna się od "Bearer "
        if (authHeader?.startsWith("Bearer ")) {
            String jwt = authHeader.substring(7)

            // 2. Weryfikujemy token
            if (jwtService.isTokenValid(jwt)) {
                String username = jwtService.extractUsername(jwt)

                // 3. Tworzymy obiekt "zalogowanego użytkownika" w kontekście Springa
                def authToken = new UsernamePasswordAuthenticationToken(username, null, [])
                SecurityContextHolder.context.authentication = authToken
            }
        }

        // 4. Przekazujemy żądanie dalej (do kolejnych filtrów lub kontrolera)
        filterChain.doFilter(request, response)
    }
}