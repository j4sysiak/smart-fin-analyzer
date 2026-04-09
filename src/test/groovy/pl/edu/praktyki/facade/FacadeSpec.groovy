package pl.edu.praktyki.facade

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import pl.edu.praktyki.BaseIntegrationSpec
import pl.edu.praktyki.domain.Transaction
import pl.edu.praktyki.repository.TransactionRepository

import java.time.LocalDate

/**
 * Test integracyjny Fasady — orkiestruje cały proces biznesowy.
 *
 * Profil 'tc' jest dziedziczony z BaseIntegrationSpec — PostgreSQL
 * startuje automatycznie w kontenerze Docker (Docker CLI, port 15432).
 *
 * KNOW HOW — ręczna inspekcja bazy (profil 'local-pg'):
 *   Jeśli chcesz podejrzeć dane w bazie po teście, odkomentuj poniższą adnotację
 *   i uruchom ręcznie kontener PostgreSQL (patrz: application-local-pg.properties):
 *     @ActiveProfiles(value = ["local-pg"], inheritProfiles = false)
 *   Wtedy test użyje localhost:5432 zamiast automatycznego kontenera.
 */
@AutoConfigureMockMvc
class FacadeSpec extends BaseIntegrationSpec {

    // Wstrzykujemy TYLKO Fasadę
    //   - nie interesują nas poszczególne serwisy, repozytoria, ani szczegóły implementacji.
    @Autowired SmartFinFacade smartFinFacade

    @Autowired
    TransactionRepository repository


    def setup() {
        // Przed każdym testem czyścimy bazę i dodajemy świeże dane
        repository.deleteAll()
    }

    def "powinien przetworzyć cały proces biznesowy przez jeden punkt dostępu (Fasada)"() {
        given: "dane wejściowe od użytkownika (np. z CLI lub z REST Controller)"
        def userName = "Testowy Użytkownik"
        def data =[
                new Transaction(id: "F1", amount: 100.0, currency: "PLN", category: "Test", date: LocalDate.now())
        ]
        def rules =["if (amountPLN < -100) addTag('BIG_SPENDER')"]

        when: "wywołujemy GŁÓWNĄ metodę Fasady"
        // Klient (nasz test) nie wie o istnieniu repozytoriów, walut ani reguł.
        // Wywołuje tylko jedną metodę, a Fasada orkiestruje resztę.
        def generatedReport = smartFinFacade.processAndGenerateReport(userName, data, rules)

        then: "cały proces zakończył się sukcesem, zwracając gotowy raport"
        generatedReport != null
        generatedReport.contains("RAPORT FINANSOWY DLA: TESTOWY UŻYTKOWNIK")

        and: "raport zawiera przetworzone dane (np. 100 PLN na plusie)"
        generatedReport.contains("Status: NA PLUSIE")

        // Opcjonalnie: Wydrukuj raport na konsolę, aby zobaczyć efekt pracy Fasady
        println ">>> RAPORT Z FASADY:\n$generatedReport"
    }
}