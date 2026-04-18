package pl.edu.praktyki.security

import groovy.transform.Canonical
import io.swagger.v3.oas.annotations.media.Schema

@Canonical
class RegisterRequest {
    @Schema(example = "user1", description = "Nazwa użytkownika")
    String username

    @Schema(example = "secret", description = "Hasło użytkownika")
    String password

    @Schema(example = "ROLE_USER", description = "Rola (np. ROLE_USER lub ROLE_ADMIN)")
    String role
}

