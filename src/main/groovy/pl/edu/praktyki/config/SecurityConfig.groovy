package pl.edu.praktyki.config

import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Bean
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.util.matcher.AntPathRequestMatcher

@Configuration
class H2ConsoleSecurityConfig {

    @Bean
    SecurityFilterChain h2SecurityFilterChain(HttpSecurity http) throws Exception {
        // This security chain should ONLY apply to the H2 console paths.
        // Use a securityMatcher so other SecurityFilterChain beans (e.g. the
        // main application JWT-based security) remain responsible for the
        // rest of the application.
        http.securityMatcher(new AntPathRequestMatcher('/h2-console/**'))

        http
            // For H2 console allow all requests that match this chain
            .authorizeHttpRequests { auth ->
                auth.anyRequest().permitAll()
            }
            // Disable CSRF only for H2 console paths
            .csrf { csrf ->
                csrf.ignoringRequestMatchers(new AntPathRequestMatcher('/h2-console/**'))
            }
            // Allow frames for H2 console
            .headers { headers ->
                headers.frameOptions().disable()
            }

        return http.build()
    }
}

