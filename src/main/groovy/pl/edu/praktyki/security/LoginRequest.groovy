package pl.edu.praktyki.security

import groovy.transform.Canonical
import io.swagger.v3.oas.annotations.media.Schema

@Canonical
class LoginRequest {
    @Schema(example = "user1", description = "Nazwa użytkownika")
    String username

    @Schema(example = "secret", description = "Hasło użytkownika")
    String password
}

