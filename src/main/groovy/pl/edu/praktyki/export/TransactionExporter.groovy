package pl.edu.praktyki.export
import pl.edu.praktyki.domain.TransactionDto

interface TransactionExporter {
    String exportRow(TransactionDto tx)
    String exportHeader()
}
