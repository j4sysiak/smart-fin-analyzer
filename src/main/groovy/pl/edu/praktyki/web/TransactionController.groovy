package pl.edu.praktyki.web

import groovy.util.logging.Slf4j
import jakarta.validation.Valid
import org.springframework.web.server.ResponseStatusException
import pl.edu.praktyki.facade.SmartFinFacade
import pl.edu.praktyki.repository.FinancialSummaryEntity
import pl.edu.praktyki.repository.FinancialSummaryRepository
import pl.edu.praktyki.repository.TransactionEntity
import pl.edu.praktyki.service.CurrencyService
import pl.edu.praktyki.service.FinancialAnalyticsService
import pl.edu.praktyki.service.TransactionRuleService
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import pl.edu.praktyki.repository.TransactionRepository
import pl.edu.praktyki.domain.Transaction
import pl.edu.praktyki.repository.TransactionSpecifications
import org.springframework.data.jpa.domain.Specification
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag

@Slf4j
@RestController
@RequestMapping("/api/transactions")
@Tag(name = "Transactions", description = "Zarządzanie operacjami finansowymi")
class TransactionController {

    @Autowired TransactionRepository repo
    @Autowired FinancialAnalyticsService analyticsService
    @Autowired CurrencyService currencyService
    @Autowired TransactionRuleService ruleService
    @Autowired SmartFinFacade facade
    @Autowired FinancialSummaryRepository summaryRepo


    // Bezpieczny endpoint dla Big Data - paginacja, żeby nie zwracać całej historii naraz
    // sortowanie i zabezpieczenie przed atakiem (klient prosi np. o 1 milion rekordów na stronie)
    @GetMapping
    Page<Transaction> getAll(
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size,
            @RequestParam(name = "sortBy", defaultValue = "date") String sortBy,
            @RequestParam(name = "direction", defaultValue = "DESC") String direction) {

        // 1. Zabezpieczenie przed atakiem (klient prosi o 1 milion rekordów na stronie)
        // tu jest nasz bezpiecznik, który obcina rozmiar strony do 100, nawet jeśli klient zażąda więcej

        // Ograniczenie dotyczy tylko rozmiaru strony (liczby rekordów na stronę), a nie liczby stron.
        // safeSize = Math.min(size, 100) oznacza: maksymalnie 100 rekordów na jedną stronę.
        // Liczba stron (totalPages) wyliczana jest jako ceil(totalElements / safeSize).
        // Jeśli masz np. 10 100 rekordów i safeSize = 100, to dostaniesz 102 strony (indeksy 0..101)
        //   — ostatnia nadal będzie dostępna przez page=101.
        // Jeśli klient zażąda strony poza zakresem, Spring Data zwróci pustą stronę
        // z metadanymi (totalPages, totalElements).
        // Jeśli chcesz dodatkowo ograniczyć maksymalny numer strony,
        // trzeba to jawnie sprawdzić i np. odrzucić żądanie lub „przyciąć” parametr page na podstawie repo.count().
        int safeSize = Math.min(size, 100)

        // 2. Budujemy obiekt Pageable
        Sort.Direction sortDir = Sort.Direction.fromString(direction.toUpperCase())
        Pageable pageable = PageRequest.of(page, safeSize, Sort.by(sortDir, sortBy))

        // 3. Pobieramy stronę encji z bazy (Hibernate wygeneruje SQL z LIMIT i OFFSET)
        def entityPage = repo.findAll(pageable)

        // 4. Magia Spring Data: Metoda .map() na obiekcie Page konwertuje elementy,
        // ZACHOWUJĄC wszystkie metadane (totalPages, totalElements, itp.)
        return entityPage.map { entity ->
            new Transaction(
                    id: entity.originalId,
                    date: entity.date,
                    amount: entity.amount,
                    currency: entity.currency,
                    amountPLN: entity.amountPLN,
                    category: (entity.category instanceof pl.edu.praktyki.repository.CategoryEntity) ? entity.category.name : entity.category,
                    description: entity.description,
                    tags: entity.tags
            )
        }
    }

    @GetMapping("/stats")
    Map<String, Object> getStats() {
        // Wywołanie paginowanej wersji getAll z domyślnymi parametrami
        def page = getAll(0, 20, "date", "DESC")

        return [
                balance: analyticsService.calculateTotalBalance(page.content),
                topCategory: analyticsService.getTopSpendingCategory(page.content),
                count: page.totalElements
        ]
    }

    @GetMapping("/{dbId}")
    Transaction getById(@PathVariable("dbId") Long dbId) {

        // Szukamy w bazie. Jeśli nie ma, od razu rzucamy wyjątek!
        def entity = repo.findById(dbId).orElseThrow {
            // .toString() jest bardzo ważne, żeby Java nie pogniewała się na GStringa z Groovy
            new ResponseStatusException(HttpStatus.NOT_FOUND, "Transakcja o ID ${dbId} nie istnieje".toString())
        }

        // Jeśli znaleziono, po prostu zwracamy obiekt
        return new Transaction(
                id: entity.originalId,
                date: entity.date,
                amount: entity.amount,
                currency: entity.currency,
                amountPLN: entity.amountPLN,
                category: (entity.category instanceof pl.edu.praktyki.repository.CategoryEntity) ? entity.category.name : entity.category,
                description: entity.description,
                tags: entity.tags
        )
    }


    // DODANY ENDPOINT POST /api/transactions
    // tutaj trafiają endpointy z formularza, CLI, importu CSV itp.
    // To jest miejsce, gdzie następuje cały magiczny processing: waluty, reguły, zapis do bazy.
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    // DODANA ADNOTACJA @Valid
    Transaction addTransaction(@Valid @RequestBody Transaction dto) {
        def rate = currencyService.getExchangeRate(dto.currency ?: "PLN")
        dto.amountPLN = dto.amount * rate

        def rules =["if (amountPLN < -100) addTag('BIG_SPENDER')"]
        ruleService.applyRules(dto, rules)

        def entity = new TransactionEntity(
                originalId: dto.id,
                date: dto.date ?: java.time.LocalDate.now(),
                amount: dto.amount,
                currency: dto.currency ?: "PLN",
                amountPLN: dto.amountPLN,
                category: dto.category,
                description: dto.description,
                tags: dto.tags
        )
        repo.save(entity)

        return dto
    }

    @PostMapping("/bulk")
    @ResponseStatus(HttpStatus.ACCEPTED) // Zwraca kod 202 - "Przyjąłem, zrobię później"
    void addBulkTransactions(@RequestBody List<Transaction> transactions) {
        log.info(">>> Otrzymano paczkę {} transakcji do przetworzenia asynchronicznego", transactions.size())

        // Nie czekamy na wynik! Odpalamy i zapominamy (Fire and forget)
        facade.processInBackgroundTask("SystemUser", transactions, [])
    }

    // CQRS Lite
    @GetMapping("/total-summary")
    Map<String, Object> getGlobalSummary() {
        def summary = summaryRepo.findById("GLOBAL")
                .orElse(new FinancialSummaryEntity())
        return [
                globalTotalBalance: summary.totalBalance,
                syncTimestamp: java.time.LocalDateTime.now()
        ]
    }

    // Przykład endpointu z paginacją i filtrowaniem po kategorii z różnymi kryteriami (kwota, opis)
    // Lab75--Big-Data-i-Optymalizacja-Hibernate
    // Lab76--Dynamiczne-Filtrowanie--Spring-Data-Specifications
    @GetMapping("/search")
    @Operation(
            summary = "Dynamiczne wyszukiwanie transakcji",
            description = "Pozwala filtrować transakcje po kategorii, kwocie i opisie z wykorzystaniem paginacji."
    )
    @ApiResponse(responseCode = "200", description = "Lista znaleziona pomyślnie")
    Page<Transaction> search(
            @RequestParam(value = "category", required = false) String category,
            @RequestParam(value = "minAmount", required = false) BigDecimal minAmount,
            @RequestParam(value = "description", required = false) String description,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "10") int size) {

        // 1. Zabezpieczenie przed atakiem (OOM Protection)
        int safeSize = Math.min(size, 100)


        // 2. Łączymy filtry (Zasada: dodaj tylko te, które użytkownik podał)
        def spec = Specification
                .where(TransactionSpecifications.hasCategory(category))
                .and(TransactionSpecifications.amountGreaterThan(minAmount))
                .and(TransactionSpecifications.descriptionLike(description))


        // 3. Budujemy obiekt Paginacji (Domyślnie sortujemy po dacie malejąco)
        def pageable = org.springframework.data.domain.PageRequest.of(
                page,
                safeSize,
                org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "date")
        )

        // 4. Wykonujemy zapytanie z paginacją
        // Pobieramy "stronę" danych (nie wszystkie naraz!)
        def entitiesPage = repo.findAll(spec, pageable)

        // 4. Mapujemy na DTO
        return entitiesPage.map { ent ->
            new Transaction(
                    id: ent.originalId,
                    amountPLN: ent.amountPLN,
                    category: (ent.category instanceof pl.edu.praktyki.repository.CategoryEntity) ? ent.category.name : ent.category,
                    description: ent.description)
        }
    }
}