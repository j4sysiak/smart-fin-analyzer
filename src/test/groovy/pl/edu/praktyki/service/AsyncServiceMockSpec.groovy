package pl.edu.praktyki.service

import spock.lang.Specification
import pl.edu.praktyki.repository.TransactionRepository
import pl.edu.praktyki.domain.Transaction

class AsyncServiceMockSpec extends Specification {

    // 1. Tworzymy MOCKA repozytorium
    def repoMock = Mock(TransactionRepository)

    // 2. Wstrzykujemy mocka przez konstruktor (ręczny DI)
    def service = new AsyncService(repoMock)

    def "powinien wywołać save na repozytorium przy zapisie"() {
        given: "transakcja"
        def tx = new Transaction(id: "TEST-1", amount: 100)

        when: "wywołujemy metodę zapisu"
        service.saveTransaction(tx)

        then: "weryfikujemy, że metoda save na repozytorium została wywołana dokładnie raz"
        1 * repoMock.save(_) // Sprawdzamy interakcję!
    }
}