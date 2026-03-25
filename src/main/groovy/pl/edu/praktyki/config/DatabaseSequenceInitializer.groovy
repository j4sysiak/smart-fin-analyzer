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
            jdbc.execute("CREATE SEQUENCE IF NOT EXISTS tx_seq START WITH 1 INCREMENT BY 1")
            log.info('>>> [DB INIT] Sequence tx_seq is present (or created)')
        } catch (Exception e) {
            log.warn('>>> [DB INIT] Could not ensure sequence tx_seq: {}', e.message)
        }
    }
}

