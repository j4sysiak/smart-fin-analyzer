package pl.edu.praktyki.domain

import groovy.transform.Canonical
import java.time.LocalDate

@Canonical
class Transaction {
    String id
    LocalDate date
    BigDecimal amount
    String category
    String description

    // Dodatkowa metoda pomocnicza - czy to wydatek czy wpływ?
    boolean isExpense() {
        return amount < 0
    }
}