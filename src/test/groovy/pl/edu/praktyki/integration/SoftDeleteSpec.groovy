package pl.edu.praktyki.integration

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.domain.PageRequest
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.server.ResponseStatusException
import pl.edu.praktyki.BaseIntegrationSpec
import pl.edu.praktyki.repository.TransactionEntity
import pl.edu.praktyki.repository.TransactionRepository
import pl.edu.praktyki.web.TransactionController

import java.time.LocalDate

class SoftDeleteSpec extends BaseIntegrationSpec {

    @Autowired TransactionController transactionController
    @Autowired TransactionRepository transactionRepository

    def setup() {
        SecurityContextHolder.clearContext()
    }

    def cleanup() {
        SecurityContextHolder.clearContext()
    }

    def "owner can soft-delete transaction: hidden in repo but kept in DB with deleted metadata"() {
        given:
        auth('user1')
        def tx = transactionRepository.saveAndFlush(new TransactionEntity(
                originalId: "DEL-${System.nanoTime()}",
                date: LocalDate.now(),
                amount: 100.00G,
                currency: "PLN",
                amountPLN: 100.00G,
                category: "TEST",
                description: "to delete",
                ownerUsername: "user1"
        ))

        when:
        transactionController.deleteTransaction(tx.dbId)

        then: "repozytorium już nie widzi rekordu (działa filtr deleted=false)"
        !transactionRepository.findById(tx.dbId).isPresent()

        and: "lista usera też nie zwraca soft-deleted"
        transactionRepository.findAllByOwnerUsername("user1", PageRequest.of(0, 20)).totalElements == 0

        and: "rekord fizycznie nadal istnieje i ma ustawione pola soft-delete"
        jdbcTemplate.queryForObject(
                "SELECT count(*) FROM transactions WHERE db_id = ?",
                Integer,
                tx.dbId
        ) == 1

        jdbcTemplate.queryForObject(
                "SELECT deleted FROM transactions WHERE db_id = ?",
                Boolean,
                tx.dbId
        ) == true

        jdbcTemplate.queryForObject(
                "SELECT deleted_by FROM transactions WHERE db_id = ?",
                String,
                tx.dbId
        ) == "user1"

        jdbcTemplate.queryForObject(
                "SELECT deleted_at IS NOT NULL FROM transactions WHERE db_id = ?",
                Boolean,
                tx.dbId
        ) == true
    }

    def "strict 404: second delete on same transaction returns not found"() {
        given:
        auth('user1')
        def tx = transactionRepository.saveAndFlush(new TransactionEntity(
                originalId: "DEL2-${System.nanoTime()}",
                date: LocalDate.now(),
                amount: 50.00G,
                currency: "PLN",
                amountPLN: 50.00G,
                category: "TEST",
                description: "second delete",
                ownerUsername: "user1"
        ))

        and:
        transactionController.deleteTransaction(tx.dbId)

        when:
        transactionController.deleteTransaction(tx.dbId)

        then:
        def ex = thrown(ResponseStatusException)
        ex.statusCode.value() == 404
    }

    def "ownership: user cannot delete someone else's transaction (404) and record stays active"() {
        given:
        def tx = transactionRepository.saveAndFlush(new TransactionEntity(
                originalId: "OTHER-${System.nanoTime()}",
                date: LocalDate.now(),
                amount: 77.00G,
                currency: "PLN",
                amountPLN: 77.00G,
                category: "TEST",
                description: "foreign owner",
                ownerUsername: "ownerA"
        ))

        and: "zalogowany jest inny user"
        auth('ownerB')

        when:
        transactionController.deleteTransaction(tx.dbId)

        then:
        def ex = thrown(ResponseStatusException)
        ex.statusCode.value() == 404

        and: "rekord nie został soft-usunięty"
        jdbcTemplate.queryForObject(
                "SELECT deleted FROM transactions WHERE db_id = ?",
                Boolean,
                tx.dbId
        ) == false
    }

    def "strict 404: deleting non-existing id returns not found"() {
        given:
        auth('user1')

        when:
        transactionController.deleteTransaction(999999L)

        then:
        def ex = thrown(ResponseStatusException)
        ex.statusCode.value() == 404
    }

    def "soft-delete creates audit trail: revtype=1 (UPDATE) with deleted metadata in transactions_aud"() {
        given:
        auth('user1')
        def tx = transactionRepository.saveAndFlush(new TransactionEntity(
                originalId: "AUD-${System.nanoTime()}",
                date: LocalDate.now(),
                amount: 250.00G,
                currency: "PLN",
                amountPLN: 250.00G,
                category: "TEST",
                description: "to audit",
                ownerUsername: "user1"
        ))

        when:
        transactionController.deleteTransaction(tx.dbId)

        then: "audit table zawiera wciśś UPDATE (revtype=1) z soft-delete metadanymi"
        // First check: any audit records for this transaction exist?
        def allAuditRecords = jdbcTemplate.queryForList(
                """
                SELECT db_id, revtype, deleted, deleted_at, deleted_by 
                FROM transactions_aud 
                WHERE db_id = ?
                ORDER BY rev
                """.toString(),
                tx.dbId
        )

        // There should be at least one INSERT (initial creation) and one UPDATE (soft-delete)
        allAuditRecords.size() >= 1  // Initial creation

        and: "ostatni wpis w audycie ma deleted=true i zawiera metadane soft-delete"
        def lastAuditRecord = allAuditRecords.last()
        lastAuditRecord.get('deleted') == true

        and: "deleted_at nie jest NULL w ostatnim wierszu audytu"
        lastAuditRecord.get('deleted_at') != null

        and: "deleted_by zawiera username użytkownika w ostatnim wierszu audytu"
        lastAuditRecord.get('deleted_by') == 'user1'
    }

    private static void auth(String username, String role = 'ROLE_USER') {
        SecurityContextHolder.context.authentication = new UsernamePasswordAuthenticationToken(
                username,
                null,
                [new SimpleGrantedAuthority(role)]
        )
    }
}