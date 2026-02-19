Skoro fundamenty działają i debugger przestał nas rozpraszać, wracamy do realizacji Kroku 2: Wielowątkowy Importer (GPars).

To jest moment, w którym Twój projekt zaczyna nabierać profesjonalnych kształtów – nauczymy aplikację wykorzystywać pełną moc procesora.

Krok 2.1: Implementacja w serwisie (TransactionIngesterService.groovy)
----------------------------------------------------------------------

Zaktualizuj teraz swój serwis. 
Dodamy metodę, która przyjmuje "paczki" danych (symulujące np. pliki z różnych banków) i przetwarza je równolegle.

```groovy
package pl.edu.praktyki.service

import org.springframework.stereotype.Service
import pl.edu.praktyki.domain.Transaction
import groovyx.gpars.GParsPool

@Service
class TransactionIngesterService {

    // Poprzednia prosta metoda (baseline)
    List<Transaction> ingestTransactions(List<Transaction> rawData) {
        return rawData
    }

    /**
     * NOWA METODA: Przyjmuje listę list transakcji.
     * Przetwarza każdą wewnętrzną listę w osobnym wątku.
     */
    List<Transaction> ingestFromMultipleSources(List<List<Transaction>> allSources) {
        // Sprawdzenie krawędziowe
        if (!allSources) return []

        // withPool() inicjalizuje silnik wielowątkowy GPars
        GParsPool.withPool {
            // collectParallel sprawia, że każda paczka (source) 
            // jest przetwarzana równolegle na innym rdzeniu procesora
            def parallelResults = allSources.collectParallel { List<Transaction> source ->
                println "[GPars] Przetwarzam paczkę danych (${source.size()} szt.) w wątku: ${Thread.currentThread().name}"
                
                // Tutaj mogłaby być dodatkowa logika, np. filtrowanie duplikatów wewnątrz paczki
                return source
            }
            
            // Ponieważ collectParallel zwraca List<List<Transaction>>, 
            // używamy flatten(), aby otrzymać jedną, płaską listę transakcji.
            return parallelResults.flatten()
        }
    }
}
```

Krok 2.2: Test w Spocku (TransactionIngesterSpec.groovy)
--------------------------------------------------------

Teraz zweryfikujemy, czy łączenie danych działa i czy suma wszystkich kwot po "spłaszczeniu" listy jest poprawna.

Dopisz ten test do pliku TransactionIngesterSpec.groovy:

```groovy
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
```

Co zyskujesz w tym kroku?
-------------------------

Skalowalność: 
Twoja aplikacja nie boi się już dużej ilości danych. 
Jeśli jutro będziesz miał 100 plików do zaimportowania, GPars automatycznie rozłoży pracę na wszystkie dostępne rdzenie Twojego komputera.

Fluent API: 
Zobacz, jak czysty jest kod testu. 
`finalResults.amount.sum()` – zero pętli, zero ręcznego mapowania. 
To jest prawdziwy "Groovy Style".

Bezpieczeństwo wątków: 
`GParsPool` dba o to, aby wątki zostały posprzątane po zakończeniu operacji.