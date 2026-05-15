package pl.edu.praktyki.security

import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Service

@Service
class UserContextService {

    String getCurrentUsername() {
        def auth = SecurityContextHolder.context.authentication
        if (auth == null || !auth.authenticated || auth.principal == "anonymousUser") {
            return "SYSTEM"
        }
        return auth.name
    }
}