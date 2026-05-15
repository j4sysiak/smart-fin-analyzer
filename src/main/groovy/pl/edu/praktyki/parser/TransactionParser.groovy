package pl.edu.praktyki.parser

import pl.edu.praktyki.domain.TransactionDto

interface TransactionParser {

    List<TransactionDto> parse(File file)
}