package pl.edu.praktyki.web

import groovy.util.logging.Slf4j
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException
import pl.edu.praktyki.domain.TransactionDto
import pl.edu.praktyki.facade.SmartFinFacade
import pl.edu.praktyki.repository.FinancialSummaryEntity
import pl.edu.praktyki.repository.FinancialSummaryRepository
import pl.edu.praktyki.service.FinancialAnalyticsService
import pl.edu.praktyki.service.TransactionService
import pl.edu.praktyki.service.ExportService
import jakarta.servlet.http.HttpServletResponse
import java.io.OutputStreamWriter
import java.io.ByteArrayOutputStream
import java.time.LocalDateTime
import java.nio.charset.StandardCharsets
import org.springframework.dao.DataIntegrityViolationException

@Slf4j
@RestController
@RequestMapping("/api/transactions")
@Tag(name = "Transactions", description = "Zarządzanie operacjami finansowymi z izolacją danych")
class TransactionController {

    private static final byte[] UTF8_BOM = [0xEF, 0xBB, 0xBF] as byte[]

    @Autowired TransactionService transactionService
    @Autowired FinancialAnalyticsService analyticsService
    @Autowired SmartFinFacade facade
    @Autowired FinancialSummaryRepository summaryRepo
    @Autowired ExportService exportService

    @GetMapping
    @Operation(summary = "Pobierz transakcje zalogowanego użytkownika")
    Page<TransactionDto> getAll(
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size,
            @RequestParam(name = "sortBy", defaultValue = "date") String sortBy,
            @RequestParam(name = "direction", defaultValue = "DESC") String direction) {

        int safeSize = Math.min(size, 100)
        Sort.Direction sortDir = Sort.Direction.fromString(direction.toUpperCase())
        Pageable pageable = PageRequest.of(page, safeSize, Sort.by(sortDir, sortBy))

        // Delegacja do serwisu, który sam wie jak przefiltrować dane po owner_username
        return transactionService.getMyTransactions(pageable)
    }

    @GetMapping("/stats")
    @Operation(summary = "Pobierz statystyki finansowe zalogowanego użytkownika - a nie globalne statystyki systemu")
    def getStats() {
        // Pobieramy dane użytkownika (pierwsze 100 sztuk do statystyk)
        def page = transactionService.getMyTransactions(PageRequest.of(0, 100))
        List<TransactionDto> list = page.content

        Map<String, Object> result = new LinkedHashMap<>()
        result.balance = analyticsService.calculateTotalBalance(list)
        result.topCategory = analyticsService.getTopSpendingCategory(list)
        result.count = page.totalElements

        return result
    }

    @GetMapping("/export")
    @Operation(summary = "Eksportuj moje transakcje do pliku CSV")
    void exportCsv(HttpServletResponse response) {
        String filename = exportService.buildDownloadFilename()

        // Przygotowujemy CSV w buforze przed wysłaniem do klienta
        ByteArrayOutputStream baos = new ByteArrayOutputStream()
        baos.write(UTF8_BOM)

        def writer = new OutputStreamWriter(baos, StandardCharsets.UTF_8)
        Map<String, Object> exportStats = exportService.exportToCsv(writer)
        writer.flush()
        writer.close()

        byte[] csvData = baos.toByteArray()
        long contentLength = csvData.length

        // Ustawienie nagłówków HTTP
        response.setCharacterEncoding(StandardCharsets.UTF_8.name())
        response.setContentType("text/csv; charset=UTF-8")
        response.setHeader("Content-Disposition", "attachment; filename=\"${filename}\"")
        response.setHeader("Cache-Control", "no-store, no-cache, must-revalidate, max-age=0")
        response.setHeader("Pragma", "no-cache")
        response.setHeader("Expires", "0")
        response.setContentLength(contentLength as int)

        log.info(">>> [EXPORT-HTTP] Nagłówki odpowiedzi ustawione. Plik: {}, rozmiar: {} bajtów",
                 filename, contentLength)

        // Wysłanie danych do klienta
        response.outputStream.write(csvData)
        response.outputStream.flush()
    }

    @GetMapping("/{dbId}")
    @Operation(summary = "Pobierz szczegóły transakcji po ID (tylko własne)")
    TransactionDto getById(@PathVariable("dbId") Long dbId) {
        def tx = transactionService.getMyTransactionById(dbId)

        if (tx == null) {
            log.warn(">>> Próba nieautoryzowanego dostępu lub brak rekordu ID: {}", dbId)
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Transakcja nie istnieje lub brak uprawnień")
        }

        return tx
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Dodaj nową transakcję", description = "System automatycznie przypisze Cię jako właściciela.")
    TransactionDto addTransaction(@Valid @RequestBody TransactionDto dto) {
        try {
            // Serwis zajmie się walutami, regułami i ustawieniem ownerUsername
            return transactionService.createTransaction(dto)
        } catch (DataIntegrityViolationException ex) {
            // Konflikt unique może wystąpić przy równoległych requestach tego samego originalId.
            // Tu jesteśmy już poza nieudanej transakcji createTransaction().
            def existing = transactionService.getMyTransactionByOriginalId(dto.id)
            if (existing != null) {
                log.warn(">>> [IDEMPOTENCY-RACE] Duplicate submit dla id={}, zwracam istniejący rekord", dto.id)
                return existing
            }
            throw ex
        }
    }

    @PutMapping("/{dbId}")
    @Operation(summary = "Zaktualizuj własną transakcję")
    TransactionDto updateTransaction(@PathVariable("dbId") Long dbId, @Valid @RequestBody TransactionDto dto) {
        def tx = transactionService.updateMyTransaction(dbId, dto)

        if (tx == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Transakcja nie istnieje lub brak uprawnień")
        }

        return tx
    }

    @DeleteMapping("/{dbId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Usuń własną transakcję (Soft Delete)")
    void deleteTransaction(@PathVariable("dbId") Long dbId) {
        boolean deleted = transactionService.deleteMyTransaction(dbId)

        if (!deleted) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Transakcja nie istnieje lub brak uprawnień")
        }
    }

    @PostMapping("/bulk")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(summary = "Masowy import asynchroniczny")
    void addBulkTransactions(@RequestBody List<TransactionDto> transactions) {
        log.info(">>> Otrzymano paczkę {} transakcji do procesowania w tle", transactions.size())
        // Fasada wciąż zarządza procesem asynchronicznym
        facade.processInBackgroundTask("SystemUser", transactions, [])
    }

    @GetMapping("/search")
    @Operation(summary = "Zaawansowane wyszukiwanie we własnych transakcjach")
    Page<TransactionDto> search(
            @RequestParam(name = "category", required = false) String category,
            @RequestParam(name = "minAmount", required = false) BigDecimal minAmount,
            @RequestParam(name = "description", required = false) String description,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "10") int size) {

        int safeSize = Math.min(size, 100)
        Pageable pageable = PageRequest.of(page, safeSize)

        // Delegacja do serwisu, który używa Specification z filtrem ownerUsername
        return transactionService.searchMyTransactions(category, minAmount, description, pageable)
    }

    @GetMapping("/total-summary")
    @Operation(summary = "Globalny bilans systemu (CQRS). Endpoint administracyjny, nie dla zwykłego użytkownika")
    @PreAuthorize("hasRole('ADMIN')")
    def getGlobalSummary() {
        def summary = summaryRepo.findById("GLOBAL").orElse(new FinancialSummaryEntity())

        Map<String, Object> result = new LinkedHashMap<>()
        result.globalTotalBalance = summary.totalBalance
        result.syncTimestamp = LocalDateTime.now().toString()

        return result
    }
}