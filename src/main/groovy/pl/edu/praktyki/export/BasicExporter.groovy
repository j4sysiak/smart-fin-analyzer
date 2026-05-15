package pl.edu.praktyki.export

import pl.edu.praktyki.domain.TransactionDto

class BasicExporter implements TransactionExporter {

    @Override
    String exportRow(TransactionDto tx) {
        return "TX: ${tx.id} | KWOTA: ${tx.amountPLN}"
    }

    @Override
    String exportHeader() {
        return "--- LISTA TRANSAKCJI ---"
    }
}