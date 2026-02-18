package pl.edu.praktyki.service

import spock.lang.Specification
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.ContextConfiguration // ZMIANA
import pl.edu.praktyki.domain.Transaction
import java.time.LocalDate

// Używamy @ContextConfiguration zamiast @SpringBootTest dla lepszej kontroli
@ContextConfiguration(classes = [TransactionIngesterService])
class TransactionIngesterSpec extends Specification {

    @Autowired
    TransactionIngesterService ingesterService

    def "powinien poprawnie zainicjować model transakcji i serwis"() {
        expect: "serwis nie jest nullem (Spring go wstrzyknął)"
        ingesterService != null

        when: "tworzymy transakcję"
        def tx = new Transaction(id: "1", amount: -50.0, category: "Kawa")

        then: "logika domeny działa"
        tx.isExpense()
    }
}