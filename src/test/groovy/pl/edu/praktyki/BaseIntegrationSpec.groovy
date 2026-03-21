package pl.edu.praktyki

import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer
import spock.lang.Specification

// 1. Główne adnotacje Spring Boota - definiujemy raz!
@SpringBootTest(classes = [SmartFinDbApp])
@ContextConfiguration // Wymagane przez Spock-Spring 2.3 do aktywacji SpringExtension
@ActiveProfiles("test")
abstract class BaseIntegrationSpec extends Specification {

    // 2. Statyczny kontener - uruchomi się TYLKO RAZ dla całego zestawu testów
    // Zmienna kontenera — może pozostać null gdy chcemy użyć lokalnego PostgreSQL (profil local-pg)
    static PostgreSQLContainer<?> postgres

    // Nie uruchamiamy Testcontainers w static initializerze — zrobimy to leniwie
    // w configureProperties, ponieważ wtedy mamy pewność, że system properties
    // i profile ustawione przez Gradle/IDE są już widoczne.

    // 3. Dynamiczne wstrzyknięcie danych logowania do Springa
    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        // Sprawdzamy aktywne profile z system properties lub zmiennych środowiskowych.
        def active = System.getProperty("spring.profiles.active") ?: System.getenv("SPRING_PROFILES_ACTIVE")
        def activeProfiles = (active == null) ? [] : active.tokenize(',').collect { it.trim() }

        def useLocalEnv = System.getenv("USE_LOCAL_PG") ?: System.getProperty("useLocalPg")
        def useLocal = false
        if (useLocalEnv != null) {
            useLocal = ['true', '1', 'yes'].contains(useLocalEnv.toLowerCase())
        }

        if (!useLocal && !activeProfiles.contains('local-pg')) {
            // Uruchamiamy Testcontainers tylko wtedy gdy nie proszono o lokalny Postgres
            if (postgres == null) {
                postgres = new PostgreSQLContainer<>("postgres:16-alpine")
                postgres.start()
            }

            registry.add("spring.datasource.url", { postgres.getJdbcUrl() })
            registry.add("spring.datasource.username", { postgres.getUsername() })
            registry.add("spring.datasource.password", { postgres.getPassword() })

            // Zmuszamy Hibernate do stworzenia tabel od zera w pustym Postgresie
            registry.add("spring.jpa.hibernate.ddl-auto", { "create-drop" })
            registry.add("spring.flyway.enabled", { "false" })

            // NOWOŚĆ: Wyłączamy schedulery na czas testów!
            registry.add("app.scheduling.enabled", { "false" })
        } else {
            // Pozwól konfiguracji (np. application-local-pg.properties) sterować połączeniem
        }
    }
}