package pl.edu.praktyki.web

import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import pl.edu.praktyki.security.UserDto
import pl.edu.praktyki.security.UserRepository

@RestController
@RequestMapping("/api/users")
@Slf4j
class UserController {

    @Autowired UserRepository userRepo

    // Endpoint do pobierania listy wszystkich użytkowników. Dostępny tylko dla adminów.
    // Endpoint: /api/users, który pozwoli odczytać wszystkich użytkowników
    // Zwraca listę obiektów UserDto (zawierających tylko username i role, bez hasła).
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')") // <-- Tylko admin może pobrać listę (w bazie w tabeli users musi mieć rolę `ROLE_ADMIN`)
    List<UserDto> getAllUsers() {
        return userRepo.findAll().collect { user  ->
            new UserDto(username: user.username, role: user.role)
        }
    }
}