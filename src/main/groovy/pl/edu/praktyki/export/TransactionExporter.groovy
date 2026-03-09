package pl.edu.praktyki.export
import pl.edu.praktyki.domain.Transaction

interface TransactionExporter {
    String exportRow(Transaction tx)
    String exportHeader()
}
