package pl.edu.praktyki.export

import pl.edu.praktyki.domain.Transaction

class BasicExporter implements TransactionExporter {

    @Override
    String exportRow(Transaction tx) {
        return "TX: ${tx.id} | KWOTA: ${tx.amountPLN}"
    }

    @Override
    String exportHeader() {
        return "--- LISTA TRANSAKCJI ---"
    }
}