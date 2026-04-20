package pl.edu.praktyki.repository

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.test.context.support.WithMockUser
import pl.edu.praktyki.BaseIntegrationSpec

import java.time.LocalDateTime

class AuditingSpec extends BaseIntegrationSpec {

    @Autowired
    TransactionRepository repository

    def setup() {
        // Czyścimy bazę przed testem audytu
        repository.deleteAll()
    }

    @WithMockUser(username = "jacek_manager")
    def "powinien automatycznie zapisać informację o autorze i dacie utworzenia (JPA Auditing)"() {
        given: "nowa encja transakcji (nie ustawiamy pól audytowych ręcznie!)"
        def entity = new TransactionEntity(
                originalId: "AUDIT-TEST-1",
                amount: 1000.0,
                amountPLN: 1000.0,
                category: "PROCES",
                description: "Test automatycznego audytu"
        )

        when: "zapisujemy encję do bazy danych"
        def saved = repository.saveAndFlush(entity)

        then: "Spring Security przekazał nazwę użytkownika do JPA"
        saved.createdBy == "jacek_manager"
        saved.lastModifiedBy == "jacek_manager"

        and: "pola daty zostały automatycznie wypełnione"
        saved.createdDate != null
        saved.lastModifiedDate != null

        // Sprawdzamy czy data jest z dzisiaj (z dokładnością do minuty)
        saved.createdDate.isBefore(LocalDateTime.now().plusSeconds(1))

        and: "wyświetlamy dowód w logach"
        println "--------------------------------------------------------"
        println "LOG AUDYTOWY:"
        println "Autor: ${saved.createdBy}"
        println "Data utworzenia: ${saved.createdDate}"
        println "--------------------------------------------------------"
    }

    def "powinien oznaczyć wpis jako SYSTEM gdy brak zalogowanego użytkownika"() {
        given: "transakcja zapisywana bez kontekstu security (np. przez automat)"
        def entity = new TransactionEntity(originalId: "SYS-1", amount: 10.0, category: "SYS")

        when:
        def saved = repository.saveAndFlush(entity)

        then: "zadziałała logika z Twojej klasy SecurityAuditorAware"
        saved.createdBy == "SYSTEM"
    }
}