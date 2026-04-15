Lab75
-----

Lab75--Big-Data-i-Optymalizacja-Hibernate
=========================================
Wchodzimy w Etap 3: Big Data i Optymalizacja Hibernate.
Obecnie Twój system potrafi szybko zapisywać dane (BulkSaver), ale co się stanie, gdy w bazie będzie milion rekordów, a Ty zrobisz:
SELECT * FROM transactions (OOM Error!)
SELECT * FROM transactions WHERE category = 'Jedzenie' (Baza będzie "mielić" całą tabelę, bo nie ma indeksu).

Cel:
Dodanie indeksów do bazy danych, aby wyszukiwanie po kategorii i dacie było natychmiastowe.

Stworzenie endpointu do wyszukiwania, który wymusza Paginację (stronicowanie) – nigdy nie oddajemy miliona rekordów naraz.

Krok-1. Nowa migracja Flyway (V5__add_indexes.sql)
--------------------------------------------------
Musimy przygotować bazę na szybkie wyszukiwanie:
`src/main/resources/db/migration/V5__add_indexes.sql`

```sql
-- Indeks na kategorię - drastycznie przyspieszy filtrowanie
CREATE INDEX idx_transactions_category ON transactions(category);

-- Indeks na datę - przyspieszy raporty czasowe
CREATE INDEX idx_transactions_date ON transactions(date);
```

Krok-2. Rozbudowa Repozytorium (TransactionRepository.groovy)
-------------------------------------------------------------
Użyjemy wbudowanego w Spring Data mechanizmu Pageable.

```groovy
package pl.edu.praktyki.repository

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface TransactionRepository extends JpaRepository<TransactionEntity, Long> {

    // Spring Data sam wygeneruje zapytanie z LIMIT i OFFSET
    Page<TransactionEntity> findByCategory(String category, Pageable pageable)
}
```

Krok-3. Endpoint w Kontrolerze (TransactionController.groovy)
-------------------------------------------------------------
Dodamy endpoint, który przyjmuje parametry page i size.

```groovy
    @GetMapping("/search")
Page<Transaction> searchByCategory(
                @RequestParam("category") String category, // DODANO NAZWĘ
                @RequestParam(value = "page", defaultValue = "0") int page, // DODANO NAZWĘ
                @RequestParam(value = "size", defaultValue = "10") int size // DODANO NAZWĘ
        ) {

    // 1. Zabezpieczenie przed atakiem (OOM Protection)
    int safeSize = Math.min(size, 100)

    // 2. Budujemy obiekt Paginacji (Domyślnie sortujemy po dacie malejąco)
    def pageable = org.springframework.data.domain.PageRequest.of(
            page,
            safeSize,
            org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "date")
    )

    // 3. Pobieramy "stronę" danych
    // Pobieramy "stronę" danych (nie wszystkie naraz!)
    def entitiesPage = repo.findByCategory(category, pageable)


    // 4. Mapujemy na obiekty domenowe (DTO)
    return entitiesPage.map { ent ->
        new Transaction(
                id: ent.originalId,
                amount: ent.amount,
                category: ent.category,
                date: ent.date
        )
    }
}
```

Krok-4. Test Spock – "Big Data Protection" (BigDataSpec.groovy)
---------------------------------------------------------------
Sprawdzimy, czy gdy w bazie jest dużo rekordów, system poprawnie oddaje tylko "kawałek".

`src/test/groovy/pl/edu/praktyki/web/BigDataSpec.groovy`

```groovy
package pl.edu.praktyki.web

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.web.servlet.MockMvc
import pl.edu.praktyki.BaseIntegrationSpec
import pl.edu.praktyki.repository.TransactionEntity
import pl.edu.praktyki.repository.TransactionRepository
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@AutoConfigureMockMvc
@WithMockUser(roles = ["ADMIN"])
class BigDataSpec extends BaseIntegrationSpec {

    @Autowired MockMvc mvc
    @Autowired TransactionRepository repo

    def "powinien zwrócić tylko pierwszą stronę wyników przy wyszukiwaniu"() {
        given: "mamy 50 transakcji w kategorii 'TEST'"
        repo.deleteAll()
        def manyTransactions = (1..50).collect { i ->
            new TransactionEntity(originalId: "ID-$i", category: "TEST", amount: 10.0, date: java.time.LocalDate.now())
        }
        repo.saveAll(manyTransactions)

        when: "pytamy o kategorię TEST, prosząc o stronę 0 o rozmiarze 5"
        def response = mvc.perform(get("/api/transactions/search")
                .param("category", "TEST")
                .param("page", "0")
                .param("size", "5"))
                .andDo(org.springframework.test.web.servlet.result.MockMvcResultHandlers.print())

        then: "otrzymujemy status 200 i tylko 5 elementów"
        response.andExpect(status().isOk())
        response.andExpect(jsonPath('$.content.length()').value(5))

        and: "metadane paginacji informują o sumie 50 rekordów"
        response.andExpect(jsonPath('$.totalElements').value(50))
        response.andExpect(jsonPath('$.totalPages').value(10))
    }
}
```

Dlaczego to jest "Enterprise Hardcore"?

Indeksy: 
Pokazujesz, że wiesz jak "rozmawiać" z bazą danych, żeby nie padła pod obciążeniem.

Pagination: 
Chronisz serwer przed `OutOfMemoryError`. To jest wymóg w 100% profesjonalnych API.

Data Mapping w locie: 
Mapowanie Page<Entity> na Page<DTO> przy użyciu .map() to technika, 
której wielu Midów nie zna (próbują zamieniać na listę i psują paginację).

Twoje zadanie:
Stwórz migrację V5 z indeksami.
Zaktualizuj Repozytorium i Kontroler.
Uruchom test BigDataSpec.
Daj znać, czy paginacja "zaskoczyła"! To jest Twoja przepustka do projektów przetwarzających miliony danych. 🚀📊🐘

