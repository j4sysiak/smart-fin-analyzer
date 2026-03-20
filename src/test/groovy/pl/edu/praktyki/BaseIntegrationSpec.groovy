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
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")

    // Blok statyczny uruchamia się w momencie załadowania klasy przez JVM
    static {
        postgres.start()
    }

    // 3. Dynamiczne wstrzyknięcie danych logowania do Springa
    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", { postgres.getJdbcUrl() })
        registry.add("spring.datasource.username", { postgres.getUsername() })
        registry.add("spring.datasource.password", { postgres.getPassword() })

        // Zmuszamy Hibernate do stworzenia tabel od zera w pustym Postgresie
        registry.add("spring.jpa.hibernate.ddl-auto", { "create-drop" })
        registry.add("spring.flyway.enabled", { "false" })
    }
}