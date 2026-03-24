package pl.edu.praktyki.facade

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.test.context.ActiveProfiles
import pl.edu.praktyki.BaseIntegrationSpec
import pl.edu.praktyki.domain.Transaction
import pl.edu.praktyki.repository.TransactionRepository

import java.time.LocalDate

// 1. Wskazujemy główną klasę aplikacji, żeby Spring wiedział, co załadować
// @SpringBootTest(classes = [SmartFinDbApp])   // pełny kontekst Spring Boot

// 2. Włączamy profil "test", żeby nie odpalał się CLI Runner z parametrami
// @ActiveProfiles("test")                      // profil testowy (bez CLI)
// @ContextConfiguration                        // trigger dla Spock-Spring 2.3

@AutoConfigureMockMvc
@ActiveProfiles(value = "tc", inheritProfiles = false)

// Wymusi użycie application-local-pg.properties ale musisz mieć wlączony lokalny Postgresa!
// (nie działa z H2, bo H2 nie obsługuje funkcji SQL, których używamy w repozytorium)
// @ActiveProfiles("local-pg")
class FacadeSpec extends BaseIntegrationSpec { // <-- DZIEDZICZYMY!

    // Wstrzykujemy TYLKO Fasadę - nie interesują nas poszczególne serwisy
    @Autowired
    SmartFinFacade facade

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
        String generatedReport = facade.saveTransactionsAndGenerateReport(userName, data, rules)

        then: "cały proces zakończył się sukcesem, zwracając gotowy raport"
        generatedReport != null
        generatedReport.contains("RAPORT FINANSOWY DLA: TESTOWY UŻYTKOWNIK")

        and: "raport zawiera przetworzone dane (np. 100 PLN na plusie)"
        generatedReport.contains("Status: NA PLUSIE")

        // Opcjonalnie: Wydrukuj raport na konsolę, aby zobaczyć efekt pracy Fasady
        println ">>> RAPORT Z FASADY:\n$generatedReport"
    }
}