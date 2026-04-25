package pl.edu.praktyki.web

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.*
import org.springframework.http.MediaType
import org.springframework.web.multipart.MultipartFile
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.ResponseEntity
import pl.edu.praktyki.facade.SmartFinFacade
import pl.edu.praktyki.parser.CsvTransactionParser
import groovy.util.logging.Slf4j
import org.springframework.security.core.context.SecurityContextHolder

@RestController
@RequestMapping("/api/transactions/upload")
@Slf4j
@Tag(name = "Transactions", description = "Upload z pliku operacji finansowych")
class UploadController {

    @Autowired SmartFinFacade facade

    // Używamy bezpośrednio Twojego parsera CSV
    @Autowired CsvTransactionParser csvParser

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @org.springframework.security.access.prepost.PreAuthorize("hasRole('ADMIN')") // <-- Tylko admin może pobrać listę (w bazie w tabeli users musi mieć rolę `ROLE_ADMIN`)
    @Operation(summary = "Masowy import z pliku CSV", description = "Wymaga roli ROLE_ADMIN.")
    @ApiResponse(responseCode = "200", description = "Raport wygenerowany pomyślnie")
    @ApiResponse(responseCode = "403", description = "Brak uprawnień administratora")
    ResponseEntity<String> uploadCsv(@RequestPart("file") MultipartFile file, @RequestParam("user") String user) {

        log.info(">>> [REST-UPLOAD] Otrzymano plik: {} od użytkownika: {}", file.originalFilename, user)
        // Diagnostyka: pokaż aktualne Authentication widziane przez Spring Security
        try {
            def auth = SecurityContextHolder.context?.authentication
            log.info(">>> [REST-UPLOAD] Current Authentication: {}", auth)
        } catch (ignored) {
            // ignore logging errors
        }

        // 1. Tworzymy tymczasowy plik na dysku, aby parser mógł go przeczytać
        File tempFile = File.createTempFile("rest-upload-", ".csv")
        file.transferTo(tempFile)
        log.info(">>> [REST-UPLOAD] Tymczasowy plik: {} ({} bytes)", tempFile.absolutePath, tempFile.length())
        try {
            def sample = tempFile.withReader('UTF-8') { r ->
                r.readLine()
            }
            log.info(">>> [REST-UPLOAD] Pierwsza linia pliku (sample): {}", sample)
        } catch (Exception e) {
            log.warn(">>> [REST-UPLOAD] Nie udało się odczytać próbki z pliku: {}", e.message)
        }

        def transactions = []
        try {
            // Czytamy z pliku tymczasowego, a nie z oryginalnego obiektu MultipartFile.
            // Niektóre serwery przenoszą/dekompresują plik do własnego tmp i getInputStream()
            // może być mniej przewidywalny po transferTo(). Korzystamy więc z tempFile.
            tempFile.withInputStream { is ->
                transactions = csvParser.parseFromStream(is)
            }

            // 2. Parsujemy plik do listy obiektów Transaction
            // def transactions = csvParser.parse(tempFile)
            log.info(">>> [REST-UPLOAD] Sparsowano {} transakcji", transactions.size())
            if (!transactions || transactions.size() == 0) {
                log.warn(">>> [REST-UPLOAD] Uwaga: parser zwrócił 0 transakcji — możliwe że w przesłanym pliku nie ma zawartości lub pole FORM jest źle skonfigurowane po stronie klienta (Postman).")
                return ResponseEntity.badRequest().body("Plik nie zawiera transakcji lub został przesłany niepoprawnie. Upewnij się, że używasz form-data i pole 'file' jest typu File.")
            }

            // 3. Wywołujemy TWOJĄ istniejącą metodę w Fasadzie
            // Przekazujemy pustą listę reguł lub przykładową regułę
            String report = facade.processAndGenerateReport(user, transactions, [])

            return ResponseEntity.ok(report)
        } catch (Exception e) {
            // Logujemy pełny stacktrace — to pomoże w diagnostyce zdalnych klientów (Postman/Swagger)
            log.error("Błąd podczas przetwarzania uploadu", e)
            // Rzucamy wyjątek dalej, aby GlobalExceptionHandler go złapał
            throw e
        } finally {
            // Zawsze usuwamy plik tymczasowy po skończonej pracy
            try {
                if (tempFile && tempFile.exists()) tempFile.delete()
            } catch (ignored) {
                log.warn("Nie udało się usunąć pliku tymczasowego: {}", tempFile?.absolutePath)
            }
        }
    }
}