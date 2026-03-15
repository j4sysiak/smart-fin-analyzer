package pl.edu.praktyki.parser

import pl.edu.praktyki.domain.Transaction
import java.time.LocalDate

// Prawdziwa implementacja
class CsvTransactionParser implements TransactionParser {
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
                def cols = line.split(',')
                transactions << new Transaction(
                        id:          cols[0].trim(),
                        amount:      cols[1].trim() as BigDecimal,
                        currency:    cols[2].trim(),
                        category:    cols[3].trim(),
                        description: cols[4].trim(),
                        date:        LocalDate.parse(cols[5].trim())
                )
            }
        return transactions
    }
}