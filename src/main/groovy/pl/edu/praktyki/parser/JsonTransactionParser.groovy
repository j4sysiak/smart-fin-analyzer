package pl.edu.praktyki.parser

import groovy.json.JsonSlurper
import pl.edu.praktyki.domain.Transaction
import java.time.LocalDate

// Prawdziwa implementacja
class JsonTransactionParser implements TransactionParser {
    @Override
    List<Transaction> parse(File file) {
        println ">>> [JSON PARSER] Czytam plik: ${file.name}"

        def slurper = new JsonSlurper()
        def json = slurper.parse(file) as List<Map>

        // Symulacja parsowania pliku json
        // return[
        //         new Transaction(id: "JSON-1", amount: 5000.0, category: "Wpływ", date: LocalDate.now())
        // ]

        // Zaczytywanie prawdziwego pliku JSON
        // (zakładamy, że jest w formacie: id,amount,currency,category,description,date)
        // Pomijamy nagłówek (pierwsza linia) i symulujemy parsowanie]
        return json.collect { map ->
            new Transaction(
                    id:          map.id as String,
                    amount:      map.amount as BigDecimal,
                    currency:    map.currency as String,
                    category:    map.category as String,
                    description: map.description as String,
                    date:        LocalDate.parse(map.date as String)
            )
        }
    }
}