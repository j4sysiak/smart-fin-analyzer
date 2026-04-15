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
            new TransactionEntity(
                    originalId: "ID-$i",
                    category: "TEST",
                    amount: 10.0,
                    date: java.time.LocalDate.now()
            )
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


    def "powinien poprawnie stronicować wyniki przy użyciu nowej metody search"() {
        given: "mamy w bazie 50 transakcji"
        repo.deleteAll()
        def manyTransactions = (1..50).collect { i ->
            new TransactionEntity(
                    originalId: "ID-$i",
                    category: "VOLUME_TEST",
                    amountPLN: 10.0,
                    date: java.time.LocalDate.now()
            )
        }
        repo.saveAll(manyTransactions)

        when: "pytamy o stronę 0 o rozmiarze 5"
        def response = mvc.perform(get("/api/transactions/search")
                .param("category", "VOLUME_TEST")
                .param("page", "0")
                .param("size", "5"))

        then: "otrzymujemy dokładnie 5 rekordów z 50 dostępnych"
        response.andExpect(status().isOk())
                .andExpect(jsonPath('$.content.length()').value(5))
                .andExpect(jsonPath('$.totalElements').value(50))
                .andExpect(jsonPath('$.totalPages').value(10))
    }

    def "powinien ograniczyć rozmiar strony do bezpiecznej wartości (OOM Protection)"() {
        when: "haker prosi o milion rekordów na jednej stronie"
        def response = mvc.perform(get("/api/transactions/search")
                .param("category", "VOLUME_TEST")
                .param("size", "1000000"))

        then: "system ogranicza size do 100 (zdefiniowane w kontrolerze Math.min)"
        response.andExpect(status().isOk())
                .andExpect(jsonPath('$.size').value(100))
    }

}