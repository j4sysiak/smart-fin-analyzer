package pl.edu.praktyki

import org.springframework.boot.test.context.SpringBootTest
// Active profile is set dynamically at class initialization based on -Dlocal.pg or existing -Dspring.profiles.active
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import spock.lang.Specification
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.beans.factory.annotation.Autowired
import org.slf4j.Logger
import org.slf4j.LoggerFactory

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
abstract class BaseIntegrationSpec extends Specification {

    @Autowired
    JdbcTemplate jdbcTemplate

    // Logger used in static and instance contexts inside this base test class
    private static final Logger log = LoggerFactory.getLogger(BaseIntegrationSpec.class)

    def setup() {
        // For deterministic integration tests: truncate domain tables before each test
        if (LOCAL_PG) {
            if (!KEEP_LOCAL_DATA) {
                doTruncateDatabase()
            } else {
                System.out.println(">>> [BaseIntegrationSpec] local-pg.keepdata=true — nie trunacjuję bazy przed testem")
            }
        } else {
            // In tc (testcontainer) profile we truncate before each test to ensure isolation
            doTruncateDatabase()
        }
    }

    private void doTruncateDatabase() {
        // Acquire an advisory lock to serialize truncation across multiple test JVMs/processes
        // This prevents PostgreSQL deadlocks when concurrent processes attempt TRUNCATE/CASCADE
        final long LOCK_KEY = 0xDEADBEEFL // arbitrary numeric key
        jdbcTemplate.execute({ conn ->
            def stmt = conn.createStatement()
            try {
                System.out.println(">>> [BaseIntegrationSpec] Acquiring advisory lock for truncate (key=${LOCK_KEY})")
                stmt.execute("SELECT pg_advisory_lock(${LOCK_KEY})")

                System.out.println(">>> [BaseIntegrationSpec] Truncating domain tables before test (RESTART IDENTITY CASCADE)")
                String truncateSql = '''
            TRUNCATE TABLE 
                transaction_entity_tags, 
                transactions, 
                categories, 
                counters, 
                financial_summary 
            RESTART IDENTITY CASCADE
        '''
                stmt.execute(truncateSql)

                // restart standalone sequence if exists
                try {
                    stmt.execute("ALTER SEQUENCE tx_seq RESTART WITH 1")
                } catch (Exception ignore) {
                    // if sequence does not exist, ignore
                }

                // Ensure GLOBAL summary row exists
                try {
                    stmt.execute("INSERT INTO financial_summary (id, total_balance, transaction_count) VALUES ('GLOBAL', 0.0, 0) ON CONFLICT (id) DO NOTHING;")
                } catch (Exception ie) {
                    // ignore insertion errors here
                }
            } finally {
                try {
                    stmt.execute("SELECT pg_advisory_unlock(${LOCK_KEY})")
                } catch (Exception unlockEx) {
                    // best-effort unlock
                }
                try { stmt.close() } catch (Exception ignored) {}
            }
            return null
        } as org.springframework.jdbc.core.ConnectionCallback)
    }

    /** Czy uruchomiono z flagą -Dlocal.pg=true (ręczna inspekcja bazy) */
    static final boolean LOCAL_PG = Boolean.getBoolean("local.pg")
    // Pozwala włączyć/wyłączyć Flyway dla testów przez -Denable.flyway=true/false
    // Domyślnie w testach integracyjnych włączamy Flyway (migracje tworzą startowe dane takie jak użytkownik admin)
    static final boolean ENABLE_FLYWAY = (System.getProperty('enable.flyway') ?: 'true').toBoolean()
    // If true, keep local-pg data instead of truncating before each test
    static final boolean KEEP_LOCAL_DATA = Boolean.getBoolean('local.pg.keepdata')

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
        // Jeśli użytkownik jawnie podał -Dspring.profiles.active=... zachowujemy jego wartość.
        // W przeciwnym razie: jeśli -Dlocal.pg=true ustawiamy profil "local-pg", w przeciwnym razie "tc".
        def explicitProfile = System.getProperty('spring.profiles.active')
        if (explicitProfile == null || explicitProfile.trim().isEmpty()) {
            def chosen = LOCAL_PG ? 'local-pg' : 'tc'
            System.setProperty('spring.profiles.active', chosen)
            System.out.println(">>> [BaseIntegrationSpec] Ustawiam spring.profiles.active=${chosen} (LOCAL_PG=${LOCAL_PG})")
        } else {
            System.out.println(">>> [BaseIntegrationSpec] spring.profiles.active explicite = ${explicitProfile}")
        }

        if (LOCAL_PG) {
            System.out.println(">>> [BaseIntegrationSpec] TRYB LOCAL-PG: łączę się z localhost:${LOCAL_PG_PORT}/${LOCAL_PG_DB}")
            // Przy uruchamianiu w trybie local-pg spróbujmy automatycznie wyczyścić testową bazę
            // aby testy były deterministyczne przy kolejnych uruchomieniach.
            try {
                cleanupLocalDatabase()
            } catch (Exception e) {
                System.err.println(">>> [BaseIntegrationSpec] Nie udało się automatycznie wyczyścić local-pg: ${e.message}")
            }
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

    /**
     * Próba wyczyszczenia lokalnej bazy (używana w trybie local-pg).
     * Najpierw próbuje wykonać `docker exec smartfin-postgres psql ...`, jeśli to nie zadziała
     * próbuje wykonać lokalne `psql` pod localhost:5432.
     */
    private static void cleanupLocalDatabase() {
        System.out.println(">>> [BaseIntegrationSpec] Czyszczenie lokalnej bazy danych (${LOCAL_PG_DB}) dla trybu local-pg...")
        // Use DROP TABLE IF EXISTS for domain tables only (keep users and flyway_schema_history intact)
        def sql = "DROP TABLE IF EXISTS transaction_entity_tags CASCADE; " +
                "DROP TABLE IF EXISTS transactions CASCADE; " +
                "DROP TABLE IF EXISTS counters CASCADE; " +
                "DROP TABLE IF EXISTS financial_summary CASCADE; " +
                "DROP SEQUENCE IF EXISTS tx_seq; "

        // 1) Spróbuj przez docker exec (kontener nazwany smartfin-postgres)
        def dockerCmd = "docker exec -i smartfin-postgres psql -U ${LOCAL_PG_USER} -d ${LOCAL_PG_DB} -c \"${sql}\""
        def rc = runCmd(dockerCmd)
        if (rc == 0) {
            System.out.println(">>> [BaseIntegrationSpec] Lokalna baza wyczyszczona przez docker exec smartfin-postgres")
            return
        }

        // 2) Spróbuj lokalnego klienta psql (połączenie do localhost:5432)
        def psqlCmd = "psql -h localhost -p ${LOCAL_PG_PORT} -U ${LOCAL_PG_USER} -d ${LOCAL_PG_DB} -c \"${sql}\""
        rc = runCmd(psqlCmd)
        if (rc == 0) {
            System.out.println(">>> [BaseIntegrationSpec] Lokalna baza wyczyszczona przez lokalny psql")
            return
        }

        // Jeśli obie metody nie zadziałały, wypisz instrukcję ręczną
        System.err.println(">>> [BaseIntegrationSpec] Automatyczne czyszczenie bazy nie powiodło się.\n" +
                "Uruchom ręcznie:\n" +
                "docker exec -it smartfin-postgres psql -U ${LOCAL_PG_USER} -d ${LOCAL_PG_DB} -c \"${sql}\"\n" +
                "lub lokalnie: psql -h localhost -p ${LOCAL_PG_PORT} -U ${LOCAL_PG_USER} -d ${LOCAL_PG_DB} -c \"${sql}\"")
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
            // Jeśli w testach włączono Flyway, to migracje tworzą schemat i dane startowe
            // — wyłączamy Hibernate DDL, aby nie nadpisywał wyników migracji.
            if (ENABLE_FLYWAY) {
                registry.add("spring.jpa.hibernate.ddl-auto", () -> "none")
            } else {
                registry.add("spring.jpa.hibernate.ddl-auto", () -> "create")
            }
        }
        // Wspólne dla obu trybów
        registry.add("spring.datasource.driverClassName", () -> "org.postgresql.Driver")
        // Domyślnie w testach Flyway jest wyłączony (schemat tworzony przez Hibernate).
        // Możesz tymczasowo włączyć Flyway przez przekazanie -Denable.flyway=true
        registry.add("spring.flyway.enabled", () -> ENABLE_FLYWAY ? "true" : "false")
        registry.add("app.scheduling.enabled", () -> "false")
    }
}