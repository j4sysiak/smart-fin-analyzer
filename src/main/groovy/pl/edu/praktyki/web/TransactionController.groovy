package pl.edu.praktyki.web

import jakarta.validation.Valid
import org.springframework.web.server.ResponseStatusException
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

@RestController
@RequestMapping("/api/transactions")
class TransactionController {

    @Autowired TransactionRepository repo
    @Autowired FinancialAnalyticsService analyticsService
    @Autowired CurrencyService currencyService
    @Autowired TransactionRuleService ruleService

    /* zmieniamy na paginację, żeby nie zwracać całej historii naraz,
           ale tylko pierwszą stronę (np. 20 rekordów)
    @GetMapping
    List<Transaction> getAll() {
        // Mapujemy Encje z bazy na domenę Transaction
        return repo.findAll().collect {
            new Transaction(
                    id: it.originalId,
                    amountPLN: it.amountPLN,
                    category: it.category,
                    description: it.description,
                    date: it.date
            )
        }
    } */

    // NOWOŚĆ: Bezpieczny endpoint dla Big Data
    @GetMapping
    Page<Transaction> getAll(
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size,
            @RequestParam(name = "sortBy", defaultValue = "date") String sortBy,
            @RequestParam(name = "direction", defaultValue = "DESC") String direction) {

        // 1. Zabezpieczenie przed atakiem (klient prosi o 1 milion rekordów na stronie)
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
                    category: entity.category,
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
                category: entity.category,
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
}