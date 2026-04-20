package pl.edu.praktyki.security

import org.springframework.data.domain.AuditorAware
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component

@Component
class SecurityAuditorAware implements AuditorAware<String> {

    @Override
    Optional<String> getCurrentAuditor() {
        def auth = SecurityContextHolder.getContext().getAuthentication()

        if (auth == null || !auth.isAuthenticated() || auth.principal == "anonymousUser") {
            return Optional.of("SYSTEM") // Dla zadań w tle (Scheduler)
        }

        return Optional.of(auth.name) // Zwraca username z tokena JWT
    }
}