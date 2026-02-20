package pl.edu.praktyki.domain

import groovy.transform.Canonical
import java.time.LocalDate

@Canonical
class Transaction {
    String id
    LocalDate date
    BigDecimal amount
    String currency = "PLN" // Domyślnie PLN
    BigDecimal amountPLN    // Kwota po przeliczeniu
    String category
    String description
    List<String> tags = [] // Lista tagów nadanych przez reguły

    // Dodatkowa metoda pomocnicza - czy to wydatek czy wpływ?
    boolean isExpense() {
        return amount < 0
    }

    void addTag(String tag) {
        if (!tags.contains(tag)) tags << tag
    }
}