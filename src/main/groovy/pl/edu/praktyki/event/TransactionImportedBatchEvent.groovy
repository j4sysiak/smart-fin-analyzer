package pl.edu.praktyki.event

import pl.edu.praktyki.domain.TransactionDto

class TransactionImportedBatchEvent {
    List<TransactionDto> transactions
}

