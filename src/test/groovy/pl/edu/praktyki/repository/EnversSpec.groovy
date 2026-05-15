package pl.edu.praktyki.repository

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import pl.edu.praktyki.BaseIntegrationSpec

// Testy audytu dla:
//   - CategoryEntity (Hibernate Envers, @Audited + proste kolumny)
//   - TransactionEntity (manualny zapis do revinfo + transactions_aud,
//     bo Hibernate 6.6.13.Final wywalał ClassCastException dla tej encji)
//
// Każda operacja DB musi być w OSOBNEJ transakcji (TransactionTemplate), aby powstała
// osobna rewizja / osobny wpis auditowy.
// Historia audytu weryfikowana przez bezpośrednie SQL (jdbcTemplate) — bez AuditReaderFactory.
class EnversSpec extends BaseIntegrationSpec {

    @Autowired
    CategoryRepository categoryRepository

    @Autowired
    TransactionRepository transactionRepository

    @Autowired
    PlatformTransactionManager transactionManager

    def setup() {
        // Przed każdym testem czyścimy bazę
        transactionRepository.deleteAll()
        categoryRepository.deleteAll()
    }

    def "powinien zachować historię zmian limitu kategorii"() {
        given: "kategoria z limitem 500 zapisana w osobnej transakcji (rewizja #1)"
        def txTemplate = new TransactionTemplate(transactionManager)
        def catName = "ENVERS_TEST_${System.nanoTime()}"

        Long id = txTemplate.execute {
            def cat = new CategoryEntity(name: catName, monthlyLimit: 500.0)
            cat = categoryRepository.saveAndFlush(cat)
            cat.id
        }

        when: "zmieniamy limit na 1000 w NOWEJ transakcji (rewizja #2)"
        txTemplate.execute {
            def cat = categoryRepository.findById(id).get()
            cat.monthlyLimit = 1000.0
            categoryRepository.saveAndFlush(cat)
        }

        then: "w głównej tabeli categories jest 1000"
        txTemplate.execute {
            categoryRepository.findById(id).get().monthlyLimit
        } == 1000.0G

        and: "Envers zapisał dokładnie 2 rewizje w tabeli categories_aud"
        def auditRows = jdbcTemplate.queryForList(
            "SELECT rev, monthly_limit, revtype FROM categories_aud WHERE id = ? ORDER BY rev",
            id)
        auditRows.size() == 2

        and: "pierwsza rewizja (INSERT) zawiera oryginalny limit 500"
        (auditRows[0].monthly_limit as BigDecimal) == 500.0G
        auditRows[0].revtype == 0  // ADD

        and: "druga rewizja (UPDATE) zawiera zaktualizowany limit 1000"
        (auditRows[1].monthly_limit as BigDecimal) == 1000.0G
        auditRows[1].revtype == 1  // MOD

        cleanup: "usuwamy dane testowe"
        txTemplate.execute {
            categoryRepository.findById(id).ifPresent { categoryRepository.delete(it) }
        }
    }

    def "powinien zachować historię zmian transakcji w transactions_aud"() {
        given: "transakcja zapisana w osobnej transakcji (rewizja #1)"
        def txTemplate = new TransactionTemplate(transactionManager)
        def uniqueId = "ENVERS-TX-${System.nanoTime()}"
        def initialOwner = "audit_owner_A"
        def updatedOwner = "audit_owner_B"

        Long dbId = txTemplate.execute {
            def tx = new TransactionEntity(
                originalId: uniqueId,
                date: java.time.LocalDate.now(),
                amount: 100.0,
                currency: "PLN",
                amountPLN: 100.0,
                description: "test audytu",
                category: "TEST",
                ownerUsername: initialOwner
            )
            tx = transactionRepository.saveAndFlush(tx)
            tx.dbId
        }

        when: "zmieniamy kwotę na 200 i ownera w NOWEJ transakcji (rewizja #2)"
        txTemplate.execute {
            def tx = transactionRepository.findById(dbId).get()
            tx.amount = 200.0
            tx.amountPLN = 200.0
            tx.ownerUsername = updatedOwner
            transactionRepository.saveAndFlush(tx)
        }

        then: "Envers zapisał dokładnie 2 rewizje w tabeli transactions_aud"
        def auditRows = jdbcTemplate.queryForList(
            "SELECT rev, amount, revtype, owner_username FROM transactions_aud WHERE db_id = ? ORDER BY rev",
            dbId)
        auditRows.size() == 2

        and: "pierwsza rewizja (INSERT, revtype=0) zawiera kwotę 100 i ownera z insertu"
        (auditRows[0].amount as BigDecimal) == 100.0G
        auditRows[0].revtype == 0  // ADD
        auditRows[0].owner_username == initialOwner

        and: "druga rewizja (UPDATE, revtype=1) zawiera zaktualizowaną kwotę 200 i nowego ownera"
        (auditRows[1].amount as BigDecimal) == 200.0G
        auditRows[1].revtype == 1  // MOD
        auditRows[1].owner_username == updatedOwner

        cleanup: "usuwamy dane testowe"
        txTemplate.execute {
            transactionRepository.findById(dbId).ifPresent { transactionRepository.delete(it) }
        }
    }
}
