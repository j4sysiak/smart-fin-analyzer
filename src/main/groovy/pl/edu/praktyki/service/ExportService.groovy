package pl.edu.praktyki.service

import groovy.util.logging.Slf4j
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import pl.edu.praktyki.repository.TransactionRepository
import pl.edu.praktyki.security.UserContextService

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Service
@Slf4j
class ExportService {

    private static final DateTimeFormatter EXPORT_FILENAME_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")

    @Autowired TransactionRepository repo
    @Autowired UserContextService userContext
    @Autowired MeterRegistry meterRegistry

    String buildDownloadFilename() {
        String currentUser = sanitizeFilenamePart(userContext.getCurrentUsername())
        String timestamp = LocalDateTime.now().format(EXPORT_FILENAME_FORMAT)
        return "transactions_${currentUser}_${timestamp}.csv"
    }

    @Transactional(readOnly = true)
    Map<String, Object> exportToCsv(Writer writer) {
        String currentUser = userContext.getCurrentUsername()
        long startedAt = System.nanoTime()
        long rowCount = 0
        long bytesWritten = 0

        log.info(">>> [EXPORT] Użytkownik {} rozpoczyna eksport transakcji", currentUser)

        Timer.Sample sample = Timer.start(meterRegistry)

        try {
            writer.write("ID,Data,Kwota_PLN,Kategoria,Opis\n")
            bytesWritten += "ID,Data,Kwota_PLN,Kategoria,Opis\n".getBytes("UTF-8").length

            // Pobieramy strumień i zapisujemy linia po linii
            repo.streamAllByOwnerUsername(currentUser).withCloseable { stream ->
                stream.each { ent ->
                    String line = [
                            csvField(ent.originalId),
                            csvField(ent.date),
                            csvField(ent.amountPLN),
                            csvField(ent.categoryEntity?.name ?: ent.category),
                            csvField(ent.description)
                    ].join(',') + '\n'
                    
                    writer.write(line)
                    bytesWritten += line.getBytes("UTF-8").length
                    rowCount++

                    // Co 1000 rekordów flushujemy (dla dużych datasetów)
                    if (rowCount % 1000 == 0) {
                        writer.flush()
                        log.debug(">>> [EXPORT] Wpisano {} rekordów ({} bajtów)", rowCount, bytesWritten)
                    }
                }
            }
        } finally {
            writer.flush()
            long durationMs = Math.floorDiv(System.nanoTime() - startedAt, 1_000_000L)
            sample.stop(Timer.builder("export.csv.timer")
                    .tag("username", currentUser)
                    .description("Czas eksportu CSV do pliku")
                    .register(meterRegistry))
            
            meterRegistry.counter("export.csv.rows.total", 
                    "username", currentUser).increment(rowCount)
            meterRegistry.gauge("export.csv.bytes.total",
                    bytesWritten)
            
            log.info(">>> [EXPORT] Eksport zakończony. Łącznie {} wierszy ({} bajtów) w {} ms", 
                    rowCount, bytesWritten, durationMs)
        }
        
        return [
            "rowCount": rowCount,
            "bytesWritten": bytesWritten,
            "username": currentUser
        ]
    }

    private static String csvField(Object value) {
        if (value == null) {
            return ""
        }

        String text = value.toString()

        // Jeśli pole zawiera przecinek, cudzysłów lub nową linię, opakuj je w cudzysłowy.
        if (text.contains(",") || text.contains("\"") || text.contains("\n") || text.contains("\r")) {
            return '"' + text.replace('"', '""') + '"'
        }

        return text
    }

    private static String sanitizeFilenamePart(String value) {
        if (!value) {
            return "user"
        }

        return value.replaceAll(/[^A-Za-z0-9._-]+/, "_")
    }
}