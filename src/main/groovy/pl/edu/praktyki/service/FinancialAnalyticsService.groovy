package pl.edu.praktyki.service

import org.springframework.stereotype.Service
import pl.edu.praktyki.domain.Transaction

@Service
class FinancialAnalyticsService {

    /**
     * Oblicza całkowity bilans (Wpływy - Wydatki) w PLN.
     */
    BigDecimal calculateTotalBalance(List<Transaction> transactions) {
        // Magia Groovy: suma pola amountPLN ze wszystkich obiektów
        return transactions*.amountPLN.sum() ?: 0.0
    }

    /**
     * Grupuje wydatki według kategorii i sumuje je.
     * Zwraca Mapę: [Kategoria: SumaWydatków]
     */
    Map<String, BigDecimal> getSpendingByCategory(List<Transaction> transactions) {
        return transactions
                .findAll { it.isExpense() && it.amountPLN < 0 } // Tylko wydatki (ujemne)
                .groupBy { it.category }    // Grupowanie w Mapę [Category: List<Transaction>]
                .collectEntries { category, list ->
                    [category, list*.amountPLN.sum().abs()] // Sumujemy i bierzemy wartość bezwzględną
                }
    }

    /**
     * Znajduje transakcję o największej kwocie wydatku.
     */
    Transaction getMostExpensiveTransaction(List<Transaction> transactions) {
        // Szukamy minimum, bo wydatki są ujemne (np. -5000 < -100)
        return transactions
                .findAll { it.isExpense() }
                .min { it.amountPLN }
    }

    /**
     * Podsumowanie dzienne: ile wydano każdego dnia.
     */
    Map<java.time.LocalDate, BigDecimal> getDailySpending(List<Transaction> transactions) {
        return transactions
                .findAll { it.isExpense() }
                .groupBy { it.date }
                .collectEntries { date, list -> [date, list*.amountPLN.sum().abs()] }
                .sort() // Sortowanie po dacie (kluczu mapy)
    }



    /*
    Wyzwanie: Dodaj do serwisu metodę
    getTopSpendingCategory(List<Transaction> transactions),
         która zwróci nazwę kategorii (String), na którą wydano najwięcej pieniędzy.
    Podpowiedź: Użyj getSpendingByCategory(transactions).max { it.value }.key.
     */
    String getTopSpendingCategory(List<Transaction> transactions) {
        def spendingByCategory = getSpendingByCategory(transactions)

        if ( spendingByCategory.isEmpty ( ) ) return null // Brak wydatków

        return spendingByCategory
                .max { it.value }.key
    }
}