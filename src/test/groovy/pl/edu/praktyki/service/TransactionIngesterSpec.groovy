package pl.edu.praktyki.service

import spock.lang.Specification
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.ContextConfiguration // ZMIANA
import pl.edu.praktyki.domain.Transaction

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

    def "powinien zwrócić nienaruszoną listę transakcji (Test ingestTransactions)"() {
        given: "lista przykładowych transakcji"
        def tx1 = new Transaction(id: "TX1", amount: 150.0, category: "Zakupy", description: "Biedronka")
        def tx2 = new Transaction(id: "TX2", amount: -40.0, category: "Rozrywka", description: "Kino")
        def inputList = [tx1, tx2]

        when: "wywołujemy metodę ingestTransactions"
        def result = ingesterService.ingestTransactions(inputList)

        then: "wynikowa lista nie jest nullem"
        result != null

        and: "rozmiar listy się zgadza"
        result.size() == 2

        and: "pierwszy element ma poprawne ID i kwotę"
        result[0].id == "TX1"
        result[0].amount == 150.0

        and: "możemy sprawdzić wszystkie kategorie naraz (Groovy Style)"
        result.category == ["Zakupy", "Rozrywka"]
    }

    def "powinien połączyć dane z wielu list w jedną płaską listę (Test Kontraktu)"() {
        given: "dwie paczki danych"
        def pack1 = [new Transaction(id: "A1", amount: 100.0)]
        def pack2 = [new Transaction(id: "B1", amount: 200.0)]
        def input = [pack1, pack2]

        when: "wywołujemy nową metodę, której jeszcze nie zaimplementowaliśmy"
        // Ta linia spowoduje błąd kompilacji/uruchomienia, bo metody nie ma w serwisie
        def result = ingesterService.ingestFromMultipleSources(input)

        then: "oczekujemy, że wynik będzie płaską listą o rozmiarze 2"
        result.size() == 2
        result.amount.sum() == 300.0
    }

    def "powinien przetworzyć wiele źródeł równolegle i połączyć wyniki w jedną listę"() {
        given: "trzy niezależne paczki danych (np. dane z 3 różnych plików)"
        def pack1 = [
                new Transaction(id: "S1-1", amount: 1000.0, category: "Pensja"),
                new Transaction(id: "S1-2", amount: -200.0, category: "Zakupy")
        ]
        def pack2 = [
                new Transaction(id: "S2-1", amount: -50.0, category: "Paliwo")
        ]
        def pack3 = [
                new Transaction(id: "S3-1", amount: -300.0, category: "Czynsz"),
                new Transaction(id: "S3-2", amount: 50.0, category: "Zwrot")
        ]

        def allInput = [pack1, pack2, pack3]

        when: "uruchamiamy wielowątkowy ingester"
        def finalResults = ingesterService.ingestFromMultipleSources(allInput)

        then: "łączna liczba transakcji powinna wynosić 5 (2 + 1 + 2)"
        finalResults.size() == 5

        and: "suma wszystkich transakcji powinna być poprawna"
        // (1000 - 200 - 50 - 300 + 50) = 500
        finalResults.amount.sum() == 500.0

        and: "możemy łatwo sprawdzić czy konkretna kategoria istnieje w wynikach"
        finalResults.category.contains("Paliwo")
    }
}