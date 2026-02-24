package pl.edu.praktyki.web

import org.springframework.web.bind.annotation.*
import org.springframework.beans.factory.annotation.Autowired
import pl.edu.praktyki.repository.TransactionRepository
import pl.edu.praktyki.service.FinancialAnalyticsService
import pl.edu.praktyki.domain.Transaction

@RestController
@RequestMapping("/api/transactions")
class TransactionController {

    @Autowired TransactionRepository repo
    @Autowired FinancialAnalyticsService analyticsService

    @GetMapping
    List<Transaction> getAll() {
        // Zamiana encji na domene (uproszczona)
        return repo.findAll().collect {
            new Transaction(id: it.originalId, amountPLN: it.amountPLN, category: it.category)
        }
    }

    @GetMapping("/stats")
    Map<String, Object> getStats() {
        def all = getAll()
        return [
                balance: analyticsService.calculateTotalBalance(all),
                topCategory: analyticsSvc.getTopSpendingCategory(all)
        ]
    }
}