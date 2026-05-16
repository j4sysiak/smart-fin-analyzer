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

@Slf4j
@RestController
@RequestMapping("/api/transactions")
@Tag(name = "Transactions", description = "Zarządzanie operacjami finansowymi z izolacją danych")
class TransactionController {

    @Autowired TransactionService transactionService
    @Autowired FinancialAnalyticsService analyticsService
    @Autowired SmartFinFacade facade
    @Autowired FinancialSummaryRepository summaryRepo

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
    Map<String, Object> getStats() {
        // Pobieramy dane użytkownika (pierwsze 100 sztuk do statystyk)
        def page = transactionService.getMyTransactions(PageRequest.of(0, 100))
        def list = page.content

        return [
                balance: analyticsService.calculateTotalBalance(list),
                topCategory: analyticsService.getTopSpendingCategory(list),
                count: page.totalElements
        ]
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
        // Serwis zajmie się walutami, regułami i ustawieniem ownerUsername
        return transactionService.createTransaction(dto)
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
    Map<String, Object> getGlobalSummary() {
        def summary = summaryRepo.findById("GLOBAL").orElse(new FinancialSummaryEntity())
        return [
                globalTotalBalance: summary.totalBalance,
                syncTimestamp: java.time.LocalDateTime.now().toString()
        ]
    }
}