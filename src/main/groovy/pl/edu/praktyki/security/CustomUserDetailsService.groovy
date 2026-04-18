package pl.edu.praktyki.security

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.core.userdetails.*
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.stereotype.Service

@Service
class CustomUserDetailsService implements UserDetailsService {

    @Autowired UserRepository userRepo

    @Override
    UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        // Szukamy w naszej bazie Postgres
        UserEntity user = userRepo.findByUsername(username)
                .orElseThrow { new UsernameNotFoundException("Nie znaleziono użytkownika: $username") }

        // Mapujemy naszą encję na obiekt UserDetails, który rozumie Spring
        return User.builder()
                .username(user.username)
                .password(user.password) // Tu jest hash z bazy
                .authorities(new SimpleGrantedAuthority(user.role))
                .build()
    }
}