package pl.edu.praktyki.service

import org.springframework.stereotype.Service
import pl.edu.praktyki.domain.Transaction
import groovyx.gpars.GParsPool

@Service
class TransactionIngesterService {

    /**
     * Docelowo ta metoda będzie przyjmować listę ścieżek do plików
     * i przetwarzać je równolegle.
     */
    List<Transaction> ingestTransactions(List<Transaction> rawData) {
        // Na razie tylko zwracamy dane - logikę GPars dodamy w następnym kroku
        return rawData
    }
}