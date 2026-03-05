package pl.edu.praktyki.domain

import groovy.transform.Canonical
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import java.time.LocalDate

@Canonical
class Transaction {

    @NotBlank(message = "ID transakcji nie może być puste")
    String id

    LocalDate date

    @NotNull(message = "Kwota (amount) jest wymagana")
    BigDecimal amount

    String currency = "PLN" // Domyślnie PLN
    BigDecimal amountPLN    // Kwota po przeliczeniu

    @NotBlank(message = "Kategoria jest wymagana")
    String category

    String description
    List<String> tags = [] // Lista tagów nadanych przez reguły

    // Dodatkowa metoda pomocnicza - czy to wydatek czy wpływ?
    boolean isExpense() { amountPLN != null && amountPLN < 0 }

    void addTag(String tag) {
        if (!tags.contains(tag)) tags << tag
    }
}