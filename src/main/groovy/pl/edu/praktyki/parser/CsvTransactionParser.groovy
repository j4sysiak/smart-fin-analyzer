package pl.edu.praktyki.parser

import org.springframework.stereotype.Component
import pl.edu.praktyki.domain.Transaction
import java.time.LocalDate

// Prawdziwa implementacja
@Component // komponent Springowy
class CsvTransactionParser implements TransactionParser {

    /**
     * Jeśli true — parser jest "strict": przy błędzie parsowania rzuca wyjątek.
     * Jeśli false — parser będzie bardziej tolerancyjny i pominie niepoprawne linie.
     * Można to nadpisać w testach przez konstruktor.
     */
    boolean strictParsing = true

    // Domyślny konstruktor używany przez Spring (strictParsing == true)
    CsvTransactionParser() {}

    // Konstruktor pomocniczy dla testów (możliwość ustawienia trybu)
    CsvTransactionParser(boolean strictParsing) {
        this.strictParsing = strictParsing
    }

    @Override
    List<Transaction> parse(File file) {
        println ">>> [CSV PARSER] Czytam plik: ${file.name}"

        def transactions = []
        def lines = file.readLines()

        // Symulacja parsowania pliku csv
        // return[
        //         new Transaction(id: "CSV-1", amount: -150.0, category: "Zakupy", date: LocalDate.now())
        //

        // Zaczytywanie prawdziwego pliku CSV
        // (zakładamy, że jest w formacie: id,amount,currency,category,description,date)
        // Pomijamy nagłówek (pierwsza linia) i symulujemy parsowanie]
            lines.drop(1).each { line ->
                if (!line || line.trim().isEmpty()) return
                def cols = line.split(',')
                // Ensure we have enough columns
                if (cols.size() < 6) {
                    def msg = ">>> [CSV PARSER] Za mało kolumn: '${line}'"
                    if (strictParsing) {
                        throw new IllegalArgumentException(msg)
                    } else {
                        println msg
                        return
                    }
                }
                try {
                    def rawAmount = cols[1].trim()
                    // handle common decimal separators and stray characters
                    rawAmount = rawAmount.replace(',', '.').replaceAll("[^0-9.\\-]", '')
                    def amountBd = rawAmount ? new BigDecimal(rawAmount) : null

                    transactions << new Transaction(
                            id:          cols[0].trim(),
                            amount:      amountBd,
                            currency:    cols[2].trim(),
                            category:    cols[3].trim(),
                            description: cols[4].trim(),
                            date:        LocalDate.parse(cols[5].trim())
                    )
                } catch (Exception e) {
                    def msg = ">>> [CSV PARSER] Błąd parsowania linii: '${line}' -> ${e.class.name}: ${e.message}"
                    if (strictParsing) {
                        println msg
                        throw e
                    } else {
                        println msg
                        // swallow and continue
                    }
                }
            }
        return transactions
    }
}