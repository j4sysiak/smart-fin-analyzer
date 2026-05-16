package pl.edu.praktyki.web

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.web.servlet.MockMvc
import pl.edu.praktyki.BaseIntegrationSpec
import pl.edu.praktyki.repository.TransactionEntity
import pl.edu.praktyki.repository.TransactionRepository

import java.time.LocalDate
import java.nio.charset.StandardCharsets
import java.time.format.DateTimeFormatter

import static org.hamcrest.Matchers.containsString
import static org.hamcrest.Matchers.startsWith
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@AutoConfigureMockMvc
@WithMockUser(username = "user_export", roles = ["USER"])
class ExportSpec extends BaseIntegrationSpec {

    @Autowired MockMvc mvc
    @Autowired TransactionRepository repo

    def "powinien wygenerować i pobrać plik CSV z danymi zalogowanego użytkownika"() {
        given: "dane dwóch użytkowników"
        repo.saveAndFlush(new TransactionEntity(
                originalId: "EXP-1",
                date: LocalDate.now(),
                amountPLN: 123.45G,
                category: "TEST",
                description: "Test eksportu",
                ownerUsername: "user_export"
        ))

        repo.saveAndFlush(new TransactionEntity(
                originalId: "EXP-OTHER-1",
                date: LocalDate.now(),
                amountPLN: 999.99G,
                category: "HIDDEN",
                description: "Nie powinno być w eksporcie",
                ownerUsername: "other_user"
        ))

        when: "wywołujemy endpoint eksportu"
        def result = mvc.perform(get("/api/transactions/export"))

        then: "status 200 oraz poprawne nagłówki pliku"
        result.andExpect(status().isOk())
        // bywa text/csv;charset=UTF-8, więc sprawdzamy prefiks
                .andExpect(header().string("Content-Type", startsWith("text/csv")))
                .andExpect(header().string("Content-Disposition", containsString("attachment; filename=\"transactions_user_export_")))
                .andExpect(header().string("Content-Disposition", containsString(".csv\"")))
                .andExpect(header().string("Cache-Control", "no-store, no-cache, must-revalidate, max-age=0"))
                .andExpect(header().exists("Content-Length"))

        and: "treść CSV zawiera nagłówek i rekord właściciela"
        def bytes = result.andReturn().response.contentAsByteArray
        assert (bytes[0] & 0xFF) == 0xEF
        assert (bytes[1] & 0xFF) == 0xBB
        assert (bytes[2] & 0xFF) == 0xBF
        def content = new String(bytes, 3, bytes.length - 3, StandardCharsets.UTF_8)
        assert content.startsWith("ID,Data,Kwota_PLN,Kategoria,Opis")
        assert content.contains("EXP-1")
        assert content.contains("123.45")

        and: "treść CSV nie zawiera rekordów innego użytkownika"
        assert !content.contains("EXP-OTHER-1")
        assert !content.contains("999.99")
    }

    def "smoke: eksport dużego wolumenu działa strumieniowo i nie miesza danych innych użytkowników"() {
        given: "duża paczka rekordów dla user_export oraz kilka rekordów obcych"
        def insertSql = '''
            INSERT INTO transactions (
                db_id, original_id, date, amount, currency, amountpln,
                category, description, owner_username, deleted
            ) VALUES (nextval('tx_seq'), ?, ?, ?, ?, ?, ?, ?, ?, false)
        '''.stripIndent()

        def ownerBatchArgs = (1..5000).collect { i ->
            [
                    "BULK-${i}",
                    java.sql.Date.valueOf(LocalDate.now()),
                    i as BigDecimal,
                    'PLN',
                    i as BigDecimal,
                    'BULK',
                    "rekord-${i}",
                    'user_export'
            ] as Object[]
        }

        def otherBatchArgs = (1..20).collect { i ->
            [
                    "OTHER-${i}",
                    java.sql.Date.valueOf(LocalDate.now()),
                    (10000G + i),
                    'PLN',
                    (10000G + i),
                    'HIDDEN',
                    "obcy-${i}",
                    'other_user'
            ] as Object[]
        }

        jdbcTemplate.batchUpdate(insertSql, ownerBatchArgs)
        jdbcTemplate.batchUpdate(insertSql, otherBatchArgs)

        when: "wołamy endpoint eksportu"
        def result = mvc.perform(get("/api/transactions/export"))

        then: "eksport kończy się sukcesem i zwraca CSV"
        result.andExpect(status().isOk())
                .andExpect(header().string("Content-Type", startsWith("text/csv")))
                .andExpect(header().string("Content-Disposition", containsString("attachment; filename=\"transactions_user_export_")))
                .andExpect(header().string("Cache-Control", "no-store, no-cache, must-revalidate, max-age=0"))
                .andExpect(header().exists("Content-Length"))

        and: "w pliku jest dokładnie nagłówek + 5000 rekordów właściciela"
        def bytes = result.andReturn().response.contentAsByteArray
        assert (bytes[0] & 0xFF) == 0xEF
        assert (bytes[1] & 0xFF) == 0xBB
        assert (bytes[2] & 0xFF) == 0xBF
        def normalized = new String(bytes, 3, bytes.length - 3, StandardCharsets.UTF_8)
        def lines = normalized.readLines()
        lines.size() == 5001
        lines.first() == "ID,Data,Kwota_PLN,Kategoria,Opis"

        and: "w treści są przykładowe rekordy właściciela"
        normalized.contains("BULK-1")
        normalized.contains("BULK-777")
        normalized.contains("BULK-5000")

        and: "w treści nie ma rekordów obcego użytkownika"
        !normalized.contains("OTHER-1")
        !normalized.contains("obcy-1")

        and: "Content-Length jest większy niż 0"
        String contentLength = result.andReturn().response.getHeader("Content-Length")
        assert contentLength != null
        assert Long.parseLong(contentLength) > 0
    }

    def "powinien zwrócić plik z poprawną nazwą w formacie transactions_user_YYYYMMDD_HHMMSS.csv"() {
        given: "jedna transakcja"
        repo.saveAndFlush(new TransactionEntity(
                originalId: "FILENAME-TEST-1",
                date: LocalDate.now(),
                amountPLN: 50.0G,
                category: "TEST",
                description: "Test nazwy pliku",
                ownerUsername: "user_export"
        ))

        when: "wywołujemy endpoint eksportu"
        def result = mvc.perform(get("/api/transactions/export"))

        then: "nagłówek Content-Disposition zawiera prawidłową nazwę pliku"
        String contentDisposition = result.andReturn().response.getHeader("Content-Disposition")
        assert contentDisposition.startsWith("attachment; filename=\"transactions_user_export_")
        assert contentDisposition.endsWith(".csv\"")

        and: "nazwa zawiera datę i czas w formacie YYYYMMDD_HHMMSS"
        // Wyciągamy datę i czas z nazwy pliku
        String filename = contentDisposition.replaceAll('.*"transactions_user_export_([^"]+)\\.csv.*', '$1')
        assert filename.matches(/^\d{8}_\d{6}$/) // Pattern: YYYYMMDD_HHMMSS

        and: "data i czas powinni być niedaleko od dzisiaj (dokładny test)"
        String dateTimePart = filename
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")
        def exportedDateTime = java.time.LocalDateTime.parse(dateTimePart, formatter)
        def now = java.time.LocalDateTime.now()
        def timeDiff = java.time.temporal.ChronoUnit.MINUTES.between(exportedDateTime, now)
        assert Math.abs(timeDiff) < 2 // Powinno być z tej samej minuty
    }
}