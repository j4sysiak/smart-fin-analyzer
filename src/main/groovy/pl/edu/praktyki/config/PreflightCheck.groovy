package pl.edu.praktyki.config

import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.context.annotation.Profile
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component

import javax.sql.DataSource
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.sql.Connection
import java.time.Duration

/**
 * =====================================================
 *  PREFLIGHT CHECK — Strażnik Startu Aplikacji
 * =====================================================
 *
 * Uruchamia się zaraz po załadowaniu kontekstu Springa (przed CLI runnerem),
 * weryfikuje, czy wszystkie krytyczne zależności systemu są dostępne.
 *
 * Sprawdzane warunki:
 *  1. Połączenie z bazą danych (DataSource ping)
 *  2. Poprawność konfiguracji datasource (url, username)
 *  3. Dostępność zewnętrznego API walutowego (HTTP HEAD / GET)
 *  4. Obecność wymaganych właściwości konfiguracyjnych
 *
 * Jeśli którykolwiek z KRYTYCZNYCH sprawdzianów nie przejdzie,
 * aplikacja rzuca wyjątek i nie kontynuuje startu.
 *
 * Wzorzec: Fail-Fast — lepiej wybuchnąć głośno na starcie
 * niż cicho zawalić się w środku działania.
 */
@Component
@Order(1) // uruchom jako pierwszy spośród wszystkich ApplicationRunnerów
@Profile("!test & !tc") // preflight nie blokuje testów integracyjnych
@Slf4j
class PreflightCheck implements ApplicationRunner {

    // ── Wstrzykiwane zasoby ──────────────────────────────────────────────────

    @Autowired
    DataSource dataSource

    @Value('${spring.datasource.url:MISSING}')
    String datasourceUrl

    @Value('${spring.datasource.username:MISSING}')
    String datasourceUsername

    @Value('${currency.api.url:https://open.er-api.com/v6/latest/PLN}')
    String currencyApiUrl

    @Value('${server.port:8080}')
    int serverPort

    @Value('${app.scheduling.enabled:false}')
    boolean schedulingEnabled

    // ── Konfiguracja timeoutów ───────────────────────────────────────────────

    private static final int DB_TIMEOUT_SECONDS = 5
    private static final int HTTP_TIMEOUT_SECONDS = 5

    // ── Główna metoda ────────────────────────────────────────────────────────

    @Override
    void run(ApplicationArguments args) {
        log.info("╔══════════════════════════════════════════════════════════╗")
        log.info("║          🛫  PREFLIGHT CHECK — START                     ║")
        log.info("╚══════════════════════════════════════════════════════════╝")

        List<String> failures = []
        List<String> warnings = []

        // ── 1. Konfiguracja wymaganych właściwości ───────────────────────────
        checkRequiredProperties(failures)

        // ── 2. Połączenie z bazą danych (KRYTYCZNE) ──────────────────────────
        checkDatabaseConnectivity(failures)

        // ── 3. Dostępność zewnętrznego API walut (OSTRZEŻENIE) ───────────────
        checkCurrencyApi(warnings)

        // ── 4. Konfiguracja serwera ──────────────────────────────────────────
        checkServerConfig(warnings)

        // ── Wynik końcowy ────────────────────────────────────────────────────
        printSummary(failures, warnings)

        if (!failures.isEmpty()) {
            throw new IllegalStateException(
                    "❌ Preflight check FAILED — aplikacja nie może wystartować. " +
                    "Powody: ${failures.join(' | ')}"
            )
        }

        log.info("╔══════════════════════════════════════════════════════════╗")
        log.info("║          ✅  PREFLIGHT CHECK — PASSED                    ║")
        log.info("╚══════════════════════════════════════════════════════════╝")
    }

    // ── Sprawdzenia szczegółowe ──────────────────────────────────────────────

    private void checkRequiredProperties(List<String> failures) {
        log.info("🔍 [1/4] Sprawdzam wymagane właściwości konfiguracyjne...")

        def checks = [
            'spring.datasource.url'     : datasourceUrl,
            'spring.datasource.username': datasourceUsername,
            'currency.api.url'          : currencyApiUrl,
        ]

        checks.each { propName, value ->
            if (!value || value == 'MISSING' || value.isBlank()) {
                failures << ("Brak wymaganej właściwości: ${propName}" as String)
                log.error("  ❌ {} → BRAK / PUSTA wartość", propName)
            } else {
                // maskujemy poufne dane
                def display = propName.contains('password') ? '***' : value
                log.info("  ✅ {} = {}", propName, display)
            }
        }
    }

    private void checkDatabaseConnectivity(List<String> failures) {
        log.info("🔍 [2/4] Sprawdzam połączenie z bazą danych (timeout: {}s)...", DB_TIMEOUT_SECONDS)
        log.info("       URL: {}", datasourceUrl)

        try {
            Connection conn = dataSource.getConnection()
            boolean valid = conn.isValid(DB_TIMEOUT_SECONDS)
            conn.close()

            if (valid) {
                log.info("  ✅ Baza danych: połączenie OK")
            } else {
                failures << "Baza danych: połączenie nawiązane, ale isValid() zwróciło false"
                log.error("  ❌ Baza danych: isValid() = false")
            }
        } catch (Exception e) {
            failures << ("Baza danych: ${e.message}" as String)
            log.error("  ❌ Baza danych: BŁĄD POŁĄCZENIA — {}", e.message)
            log.debug("  Stack trace:", e)
        }
    }

    private void checkCurrencyApi(List<String> warnings) {
        log.info("🔍 [3/4] Sprawdzam dostępność Currency API (timeout: {}s)...", HTTP_TIMEOUT_SECONDS)
        log.info("       URL: {}", currencyApiUrl)

        try {
            def client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(HTTP_TIMEOUT_SECONDS))
                    .build()

            def request = HttpRequest.newBuilder()
                    .uri(URI.create(currencyApiUrl))
                    .timeout(Duration.ofSeconds(HTTP_TIMEOUT_SECONDS))
                    .GET()
                    .build()

            def response = client.send(request, HttpResponse.BodyHandlers.discarding())
            int status = response.statusCode()

            if (status >= 200 && status < 400) {
                log.info("  ✅ Currency API: HTTP {} — dostępne", status)
            } else {
                warnings << ("Currency API zwróciło HTTP ${status}" as String)
                log.warn("  ⚠️  Currency API: HTTP {} — nieoczekiwany status", status)
            }
        } catch (Exception e) {
            warnings << ("Currency API niedostępne: ${e.message}" as String)
            log.warn("  ⚠️  Currency API: NIEDOSTĘPNE — {} (aplikacja uruchomi się z fallbackiem kursów)", e.message)
        }
    }

    private void checkServerConfig(List<String> warnings) {
        log.info("🔍 [4/4] Sprawdzam konfigurację serwera...")

        if (serverPort < 1 || serverPort > 65535) {
            warnings << ("Nieprawidłowy numer portu: ${serverPort}" as String)
            log.warn("  ⚠️  server.port={} — poza zakresem [1-65535]", serverPort)
        } else {
            log.info("  ✅ server.port = {}", serverPort)
        }

        log.info("  ℹ️  app.scheduling.enabled = {}", schedulingEnabled)
    }

    private static void printSummary(List<String> failures, List<String> warnings) {
        log.info("──────────────────────────────────────────────────────────")
        log.info("📋 PODSUMOWANIE PREFLIGHT:")
        log.info("   BŁĘDY KRYTYCZNE : {}", failures.isEmpty() ? "brak" : failures.size())
        failures.each { log.error("     ❌ {}", it) }
        log.info("   OSTRZEŻENIA     : {}", warnings.isEmpty() ? "brak" : warnings.size())
        warnings.each { log.warn("     ⚠️  {}", it) }
        log.info("──────────────────────────────────────────────────────────")
    }
}

