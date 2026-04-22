package pl.edu.praktyki.service

import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import pl.edu.praktyki.repository.CategoryEntity
import pl.edu.praktyki.repository.TransactionRepository
import java.time.LocalDate

@Service
@Slf4j
class BudgetService {
    @Autowired TransactionRepository txRepo

    boolean isOverBudget(CategoryEntity category, BigDecimal newAmount) {

        // 1. Zawsze wyliczaj zakres dat wewnątrz metody (Mid-level Best Practice)
        def today = LocalDate.now()
        def start = today.withDayOfMonth(1)
        def end = today.withDayOfMonth(today.lengthOfMonth())

        // 2. Pobieramy LISTĘ transakcji z bazy
        def transactions = txRepo.findByCategoryEntityAndDateBetween(category, start, end)

        // 3. Sumujemy kwoty z listy (Magia *. wyciąga pola amountPLN, .sum() je dodaje)
        // Jeśli lista jest pusta, używamy 0.0
        BigDecimal currentSpendingSum = transactions*.amountPLN.sum() ?: 0.0

        log.info(">>> [BUDGET] Suma wydatków w kategorii {}: {} PLN. Nowa kwota: {} PLN",
                category.name, currentSpendingSum, newAmount)

        // 4. Dopiero teraz robimy .abs() na LICZBACH (BigDecimal)
        // Sumujemy wartość bezwzględną dotychczasowych wydatków i nowej kwoty
        def totalAfterNewTransaction = currentSpendingSum.abs() + newAmount.abs()

        return totalAfterNewTransaction > category.monthlyLimit
    }
}