package pl.edu.praktyki.parser

import pl.edu.praktyki.domain.Transaction


// WZORZEC NULL OBJECT: Bezpieczna atrapa!
class UnsupportedFormatParser implements TransactionParser {
    @Override
    List<Transaction> parse(File file) {
        println ">>> [BŁĄD] Format pliku ${file.name} nie jest obsługiwany!"
        return[] // Zwraca pustą listę zamiast rzucać NullPointerException!
    }
}