package pl.edu.praktyki.web

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.cloud.contract.wiremock.AutoConfigureWireMock
import org.springframework.mock.web.MockMultipartFile
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.web.servlet.MockMvc
import pl.edu.praktyki.BaseIntegrationSpec
import pl.edu.praktyki.repository.TransactionRepository
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print

@AutoConfigureMockMvc
@AutoConfigureWireMock(port = 0)
@WithMockUser(username = "admin", roles = ["ADMIN"]) // Pamiętamy o kłódce Security! (tylko ADMIN ma prawo uploadu)
class UploadControllerSpec extends BaseIntegrationSpec {

    @Autowired MockMvc mvc
    @Autowired TransactionRepository repo

    def "powinien przyjąć plik CSV i zwrócić raport finansowy"() {
        given: "treść pliku CSV w pamięci"


        // poprawny CSV: nagłówek i osobny wiersz z danymi (nowa linia oddziela rekordy)
        // Uwaga: kolumny muszą odpowiadać parserowi: id,amount,currency,category,description,date
        def csvContent = "id,amount,currency,category,description,date\nT1,100.00,PLN,Test,Opis,2026-03-01\n".getBytes()

        // to jest przykład jak można dodać więcej wierszy, aby przetestować różne scenariusze
        // plik CSV może zawierać wiele transakcji, a nasz serwis powinien je wszystkie poprawnie przetworzyć
        // to jest plik: `C:\dev\smart-fin-analyzer\transactions_upload.csv`
        /*
id,amount,currency,category,description,date
T11,250.00,EUR,Zakupy,Monitor,2026-03-25
T12,-40.00,PLN,Jedzenie,Pizza,2026-03-27
T13,5000.00,PLN,Praca,Wypłata,2026-03-28
         */



        and: "tworzymy wirtualny plik do wysłania"
        def mockFile = new MockMultipartFile(
                "file",                // nazwa parametru w kontrolerze
                "test.csv",    // nazwa pliku
                "text/csv",      // typ MIME
                csvContent                  // zawartość
        )

        and: "liczba rekordów w bazie przed uploadem"
        def beforeCount = repo.count()

        when: "wysyłamy żądanie POST multipart"
        def response = mvc.perform(multipart("/api/transactions/upload")
                .file(mockFile)
                .param("user", "Jacek_Test"))
                .andDo(print()) // <--- TO WYDRUKUJE BŁĄD W KONSOLI, jeśli wystąpi

        then: "serwer odpowiada 200 OK"
        response.andExpect(status().isOk())

        and: "odpowiedź zawiera tekst raportu"
        def content = response.andReturn().response.contentAsString
        content.contains("RAPORT FINANSOWY DLA: JACEK_TEST")

        and: "rekord został zapisany w bazie (sprawdzamy przyrost i konkretny rekord)"
        repo.count() == beforeCount + 1

        def all = repo.findAll()
        // znajdźmy rekord(y) o originalId T1 — test nie zakłada pustej bazy, tylko że nowy rekord trafił
        def newOnes = all.findAll { it.originalId == 'T1' }
        newOnes.size() >= 1
        newOnes[0].amount == 100.00.toBigDecimal()
    }
}