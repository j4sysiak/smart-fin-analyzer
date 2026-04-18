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

    // METODA DLA CLI (zostaje bez zmian)
    @Override
    List<Transaction> parse(File file) {
        // Groovy potrafi wyciągnąć InputStream z pliku automatycznie
        return parseFromStream(file.newInputStream())
    }

    /*  bedziemy uzywac streamow zamiast czytac cale pliki do pamieci, bo to jest bardziej wydajne i pozwala na lepsze zarzadzanie zasobami (np. automatyczne zamykanie strumienia po pracy)
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
    } */


    List<Transaction> parseFromStream(InputStream is) {
        println ">>> [CSV-PARSER] Przetwarzam strumień danych..."
        def transactions = []

        // Używamy withReader, aby bezpiecznie zamknąć strumień po pracy
        is.withReader('UTF-8') { reader ->
            // skip header line
            reader.readLine()
            reader.eachLine { line ->
                if (!line || line.trim().isEmpty()) return
                def cols = line.split(',')
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
                    def id = cols[0].trim()
                    def rawAmount = cols[1].trim()
                    // Robust numeric extraction: find first occurrence of a number (handles comma or dot decimals,
                    // ignores surrounding text like currency symbols). Examples handled: "-50,00", "50.00 PLN", "ca50"
                    def numMatcher = (rawAmount =~ /-?\d+[\.,]?\d*/)
                    def amountBd = null
                    if (numMatcher.find()) {
                        def found = numMatcher.group().replace(',', '.')
                        amountBd = found ? new BigDecimal(found) : null
                    } else {
                        if (strictParsing) {
                            throw new IllegalArgumentException("Nie można odczytać kwoty z: '${cols[1]}'")
                        } else {
                            println ">>> [CSV PARSER] Nie znaleziono kwoty w polu: '${cols[1]}' - pomijam linię"
                            return
                        }
                    }

                    def currency = cols[2].trim()
                    def category = cols[3].trim()
                    def description = cols[4].trim()
                    def date = LocalDate.parse(cols[5].trim())

                    transactions << new Transaction(
                            id: id,
                            amount: amountBd,
                            currency: currency,
                            category: category,
                            description: description,
                            date: date
                    )
                } catch (Exception e) {
                    def msg = ">>> [CSV PARSER] Błąd parsowania linii: '${line}' -> ${e.class.name}: ${e.message}"
                    if (strictParsing) {
                        println msg
                        throw e
                    } else {
                        println msg
                    }
                }
            }
        }
        return transactions
    }
}