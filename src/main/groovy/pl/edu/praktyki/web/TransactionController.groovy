package pl.edu.praktyki.web

import org.springframework.web.bind.annotation.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.server.ResponseStatusException
import pl.edu.praktyki.repository.TransactionEntity
import pl.edu.praktyki.repository.TransactionRepository
import pl.edu.praktyki.service.CurrencyService
import pl.edu.praktyki.service.FinancialAnalyticsService
import pl.edu.praktyki.domain.Transaction
import pl.edu.praktyki.service.TransactionRuleService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity

@RestController
@RequestMapping("/api/transactions")
class TransactionController {

    @Autowired TransactionRepository repo
    @Autowired FinancialAnalyticsService analyticsService
    @Autowired CurrencyService currencyService
    @Autowired TransactionRuleService ruleService

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
    }

    @GetMapping("/stats")
    Map<String, Object> getStats() {
        // Wykorzystujemy metodę getAll(), żeby nie duplikować mapowania
        def all = getAll()

        return [
                balance: analyticsService.calculateTotalBalance(all),
                topCategory: analyticsService.getTopSpendingCategory(all),
                count: all.size()
        ]
    }

    // ZMIENIONA METODA: Zwracamy ResponseEntity
    @GetMapping("/{dbId}")
    // ZMIANA: Musisz jawnie powiedzieć Springowi, że ta zmienna nazywa się "dbId"
    ResponseEntity<Transaction> getById(@PathVariable("dbId") Long dbId) {

        def entityOpt = repo.findById(dbId)

        // Jeśli nie znaleziono, kulturalnie zwracamy kod 404 (Not Found) bez rzucania wyjątków
        if (entityOpt.isEmpty()) {
            return ResponseEntity.notFound().build()
        }

        // Jeśli znaleziono, mapujemy i zwracamy z kodem 200 (OK)
        def entity = entityOpt.get()
        def tx = new Transaction(
                id: entity.originalId,
                date: entity.date,
                amount: entity.amount,
                currency: entity.currency,
                amountPLN: entity.amountPLN,
                category: entity.category,
                description: entity.description,
                tags: entity.tags
        )

        return ResponseEntity.ok(tx)
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    Transaction addTransaction(@RequestBody Transaction dto) {
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