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
class DynamicSearchSpec extends BaseIntegrationSpec {

    @Autowired MockMvc mvc
    @Autowired TransactionRepository repo

    def "powinien znaleźć transakcje łącząc wiele filtrów naraz"() {
        given: "mamy w bazie mieszane dane"
        repo.save(new TransactionEntity(originalId: "T1", category: "FOOD", amountPLN: 100, description: "Pizza"))
        repo.save(new TransactionEntity(originalId: "T2", category: "FOOD", amountPLN: 10, description: "Baton"))
        repo.save(new TransactionEntity(originalId: "T3", category: "WORK", amountPLN: 5000, description: "Pensja"))

        when: "szukamy kategorii FOOD z kwotą min. 50"
        def response = mvc.perform(get("/api/transactions/search")
                .param("category", "FOOD")
                .param("minAmount", "50"))

        then: "znajduje tylko Pizzę"
        response.andExpect(status().isOk())
                .andExpect(jsonPath('$.content.length()').value(1))
                .andExpect(jsonPath('$.content[0].description').value("Pizza"))
    }
}
