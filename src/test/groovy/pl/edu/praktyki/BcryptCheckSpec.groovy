package pl.edu.praktyki

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import spock.lang.Specification
import spock.lang.Ignore

@Ignore('Debug helper - disabled')
class BcryptCheckSpec extends Specification {
    def "should verify bcrypt hash for admin123"() {
        given:
        def hash = '$2a$10$8.UnVuG9HHgffUDAlk8q6OuVGkqNi6pMvL4uC3G22YlY29M7xKxDC'
        def encoder = new BCryptPasswordEncoder()

        expect:
        encoder.matches('admin123', hash)
    }
}

