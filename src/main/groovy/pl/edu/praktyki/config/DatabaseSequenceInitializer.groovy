package pl.edu.praktyki.config

import groovy.util.logging.Slf4j
import org.springframework.stereotype.Component
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.context.event.EventListener
import org.springframework.boot.context.event.ApplicationReadyEvent

@Component
@Slf4j
class DatabaseSequenceInitializer {

    @Autowired
    JdbcTemplate jdbc
    @Autowired
    org.springframework.security.crypto.password.PasswordEncoder passwordEncoder


// Metoda oznaczona @EventListener(ApplicationReadyEvent) zostanie wywołana,
// gdy Spring Boot opublikuje zdarzenie ApplicationReadyEvent — czyli po:
//  1. odświeżeniu kontekstu,
//  2. po uruchomieniu wbudowanego serwera (dla aplikacji web)
//  3. po wykonaniu wszystkich ApplicationRunner / CommandLineRunner

// Dzieje się to raz na kontekst aplikacji
// (plik src/main/groovy/pl/edu/praktyki/config/DatabaseSequenceInitializer.groovy).


    @EventListener(ApplicationReadyEvent)
    void onReady() {
        try {
            log.info('>>> [DB INIT] Ensuring sequence tx_seq exists')
            // CREATE SEQUENCE IF NOT EXISTS works on Postgres and H2 (modern versions)
            jdbc.execute("CREATE SEQUENCE IF NOT EXISTS tx_seq START WITH 1 INCREMENT BY 50")
            log.info('>>> [DB INIT] Sequence tx_seq is present (or created)')

            // Ensure default admin user has expected password (admin123) for tests.
            try {
                def pw = jdbc.queryForObject("select password from users where username = 'admin'", String.class)
                if (pw == null) {
                    log.info('>>> [DB INIT] No admin user present in DB')
                } else if (!passwordEncoder.matches('admin123', pw)) {
                    log.info('>>> [DB INIT] Admin password does not match expected test password, updating...')
                    def newHash = passwordEncoder.encode('admin123')
                    jdbc.update("update users set password = ? where username = 'admin'", newHash)
                    log.info('>>> [DB INIT] Admin password updated')
                } else {
                    log.info('>>> [DB INIT] Admin password OK')
                }
            } catch (Exception ignored) {
                // If users table doesn't exist yet or query fails, ignore — Flyway/Hibernate order may vary
            }
        } catch (Exception e) {
            log.warn('>>> [DB INIT] Could not ensure sequence tx_seq: {}', e.message)
        }
    }
}

