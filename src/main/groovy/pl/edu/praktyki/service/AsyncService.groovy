package pl.edu.praktyki.service

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import pl.edu.praktyki.domain.Transaction
import pl.edu.praktyki.repository.TransactionRepository

import java.util.concurrent.ConcurrentHashMap

@Service
class AsyncService {
    // Spring wstrzyknie to automatycznie w aplikacji
    @Autowired
    TransactionRepository transactionRepository

    // Dodajemy konstruktor, który pozwala wstrzyknąć repozytorium ręcznie (dla testów!)
    AsyncService() {}
    AsyncService(TransactionRepository transactionRepository) {
        this.transactionRepository = transactionRepository
    }

    // Symulacja bazy danych (ConcurrentHashMap jest bezpieczna wątkowo)
    def db = new ConcurrentHashMap<String, String>()

    void performActionAsync(String id, String value) {
        Thread.start {
            sleep(1500) // Praca w tle zajmuje 1.5 sekundy
            db[id] = value
        }
    }

    boolean exists(String id) {
        return db.containsKey(id)
    }

    void saveTransaction(Transaction tx) {
        // Logika biznesowa
        transactionRepository.save(tx)
    }
}