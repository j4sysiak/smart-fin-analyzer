package pl.edu.praktyki.domain

import groovy.transform.Canonical
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import java.time.LocalDate

// DTO to Data Transfer Object.
// Oznacza prosty obiekt używany do przenoszenia danych między warstwami aplikacji,
// np.:
//    - między kontrolerem a serwisem
//    - między backendem a frontendem
//    - między API a bazą danych pośrednio

// Zwykle DTO:
//    - zawiera pola z danymi
//    - może mieć proste walidacje
//    - nie powinno zawierać złożonej logiki biznesowej

// W tym pliku TransactionDto reprezentuje dane transakcji przekazywane w aplikacji, np. id, date, amount, category.
// Różnica:
//     - Entity - model powiązany z bazą danych
//     - DTO - model do wymiany danych
//     - Domain Model - model logiki biznesowej

@Canonical
class TransactionDto {

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

    String ownerUsername

    // Dodatkowa metoda pomocnicza - czy to wydatek czy wpływ?
    boolean isExpense() { amountPLN != null && amountPLN < 0 }

    void addTag(String tag) {
        if (!tags.contains(tag)) tags << tag
    }
}