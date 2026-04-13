package pl.edu.praktyki.web

import org.springframework.web.bind.annotation.*
import org.springframework.http.MediaType
import org.springframework.web.multipart.MultipartFile
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.ResponseEntity
import pl.edu.praktyki.facade.SmartFinFacade
import pl.edu.praktyki.parser.CsvTransactionParser
import groovy.util.logging.Slf4j

@RestController
@RequestMapping("/api/transactions/upload")
@Slf4j
class UploadController {

    @Autowired SmartFinFacade facade
    // Używamy bezpośrednio Twojego parsera CSV
    @Autowired CsvTransactionParser csvParser

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ResponseEntity<String> uploadCsv(@RequestPart("file") MultipartFile file, @RequestParam("user") String user) {
        log.info(">>> [REST-UPLOAD] Otrzymano plik: {} od użytkownika: {}", file.originalFilename, user)

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

        try {
            // 2. Parsujemy plik do listy obiektów Transaction
            def transactions = csvParser.parse(tempFile)
            log.info(">>> [REST-UPLOAD] Sparsowano {} transakcji", transactions.size())
            if (!transactions || transactions.size() == 0) {
                log.warn(">>> [REST-UPLOAD] Uwaga: parser zwrócił 0 transakcji — możliwe że w przesłanym pliku nie ma zawartości lub pole FORM jest źle skonfigurowane po stronie klienta (Postman).")
                return ResponseEntity.badRequest().body("Plik nie zawiera transakcji lub został przesłany niepoprawnie. Upewnij się, że używasz form-data i pole 'file' jest typu File.")
            }

            // 3. Wywołujemy TWOJĄ istniejącą metodę w Fasadzie
            // Przekazujemy pustą listę reguł lub przykładową regułę
            String report = facade.processAndGenerateReport(user, transactions, [])

            return ResponseEntity.ok(report)
        } finally {
            // Zawsze usuwamy plik tymczasowy po skończonej pracy
            tempFile.delete()
        }
    }
}