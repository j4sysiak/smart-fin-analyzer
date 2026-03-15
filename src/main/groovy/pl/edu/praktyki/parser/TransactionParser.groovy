package pl.edu.praktyki.parser

import pl.edu.praktyki.domain.Transaction

interface TransactionParser {

    List<Transaction> parse(File file)
}