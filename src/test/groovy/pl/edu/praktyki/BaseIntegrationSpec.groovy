package pl.edu.praktyki

import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import spock.lang.Specification

/**
 * Bazowa klasa dla testów integracyjnych.
 *
 * TRYB DOMYŚLNY (profil 'tc'):
 *   Automatycznie uruchamia kontener PostgreSQL przez Docker CLI na porcie 15432.
 *   Schemat tworzony przez Hibernate (ddl-auto=create).
 *   Użycie: ./gradlew test
 *
 * TRYB INSPEKCJI (profil 'local-pg'):
 *   Łączy się z ręcznie uruchomionym kontenerem PostgreSQL na porcie 5432.
 *   Schemat aktualizowany (ddl-auto=update) — dane zostają po teście do inspekcji.
 *   Użycie: ./gradlew test --tests "..." -Dlocal.pg=true
 *   (wymaga ręcznego docker run — patrz application-local-pg.properties)
 */
@SpringBootTest(classes = [SmartFinDbApp])
@ContextConfiguration(classes = [SmartFinDbApp])
@ActiveProfiles("tc")
abstract class BaseIntegrationSpec extends Specification {

    /** Czy uruchomiono z flagą -Dlocal.pg=true (ręczna inspekcja bazy) */
    static final boolean LOCAL_PG = Boolean.getBoolean("local.pg")

    // --- Konfiguracja automatycznego kontenera (tryb 'tc') ---
    static final String CONTAINER_NAME = "smartfin-test-pg"
    static final int PG_PORT = 15432
    static final String PG_DB = "testdb"
    static final String PG_USER = "test"
    static final String PG_PASS = "test"

    // --- Konfiguracja ręcznego kontenera (tryb 'local-pg') ---
    static final int LOCAL_PG_PORT = 5432
    static final String LOCAL_PG_DB = "smartfin_test"
    static final String LOCAL_PG_USER = "finuser"
    static final String LOCAL_PG_PASS = "finpass"

    static {
        if (LOCAL_PG) {
            System.out.println(">>> [BaseIntegrationSpec] TRYB LOCAL-PG: łączę się z localhost:${LOCAL_PG_PORT}/${LOCAL_PG_DB}")
        } else {
            startPostgresContainer()
        }
    }

    private static void startPostgresContainer() {
        // Usuń ewentualny stary kontener
        runCmd("docker rm -f $CONTAINER_NAME")

        // Uruchom nowy kontener PostgreSQL
        def startCmd = "docker run -d --name $CONTAINER_NAME " +
                "-p ${PG_PORT}:5432 " +
                "-e POSTGRES_DB=$PG_DB " +
                "-e POSTGRES_USER=$PG_USER " +
                "-e POSTGRES_PASSWORD=$PG_PASS " +
                "postgres:16-alpine"
        def result = runCmd(startCmd)
        if (result != 0) {
            throw new RuntimeException("Nie udało się uruchomić kontenera PostgreSQL! Exit code: $result")
        }

        // Czekaj aż PostgreSQL będzie gotowy (max 30 sekund)
        def ready = false
        for (int i = 0; i < 30; i++) {
            def check = runCmd("docker exec $CONTAINER_NAME pg_isready -U $PG_USER")
            if (check == 0) {
                ready = true
                break
            }
            Thread.sleep(1000)
        }
        if (!ready) {
            throw new RuntimeException("PostgreSQL kontener nie uruchomił się w ciągu 30 sekund!")
        }
    }

    private static void stopPostgresContainer() {
        runCmd("docker rm -f $CONTAINER_NAME")
    }

    private static int runCmd(String command) {
        try {
            def process = command.execute()
            process.consumeProcessOutput(System.out, System.err)
            return process.waitFor()
        } catch (Exception e) {
            System.err.println("Błąd podczas wykonywania: $command — ${e.message}")
            return -1
        }
    }

    @DynamicPropertySource
    static void configureDataSource(DynamicPropertyRegistry registry) {
        if (LOCAL_PG) {
            // --- Tryb inspekcji: ręczny kontener na localhost:5432 ---
            registry.add("spring.datasource.url",
                    () -> "jdbc:postgresql://localhost:${LOCAL_PG_PORT}/${LOCAL_PG_DB}")
            registry.add("spring.datasource.username", () -> LOCAL_PG_USER)
            registry.add("spring.datasource.password", () -> LOCAL_PG_PASS)
            registry.add("spring.jpa.hibernate.ddl-auto", () -> "update")
            registry.add("spring.jpa.show-sql", () -> "true")
        } else {
            // --- Tryb automatyczny: kontener Docker CLI na localhost:15432 ---
            registry.add("spring.datasource.url",
                    () -> "jdbc:postgresql://localhost:${PG_PORT}/${PG_DB}")
            registry.add("spring.datasource.username", () -> PG_USER)
            registry.add("spring.datasource.password", () -> PG_PASS)
            registry.add("spring.jpa.hibernate.ddl-auto", () -> "create")
        }
        // Wspólne dla obu trybów
        registry.add("spring.datasource.driverClassName", () -> "org.postgresql.Driver")
        registry.add("spring.flyway.enabled", () -> "false")
        registry.add("app.scheduling.enabled", () -> "false")
    }
}