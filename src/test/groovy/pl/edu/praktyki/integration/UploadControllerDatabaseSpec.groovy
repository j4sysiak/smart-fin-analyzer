package pl.edu.praktyki.integration

import pl.edu.praktyki.BaseIntegrationSpec
import pl.edu.praktyki.web.UploadController
import pl.edu.praktyki.repository.FinancialSummaryEntity
import pl.edu.praktyki.repository.TransactionRepository
import pl.edu.praktyki.repository.FinancialSummaryRepository
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.mock.web.MockMultipartFile
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.access.AccessDeniedException
// awaiting async projections
import static org.awaitility.Awaitility.await
import java.util.concurrent.TimeUnit

class UploadControllerDatabaseSpec extends BaseIntegrationSpec {

    @Autowired
    UploadController uploadController

    @Autowired
    TransactionRepository transactionRepository

    @Autowired
    FinancialSummaryRepository summaryRepo

    def setup() {
        // ensure clean DB before each test
        transactionRepository.deleteAll()
        summaryRepo.deleteAll()
        SecurityContextHolder.clearContext()
    }

    def cleanup() {
        // clean after each test to avoid cross-test pollution
        transactionRepository.deleteAll()
        summaryRepo.deleteAll()
        SecurityContextHolder.clearContext()
    }

    def "upload CSV persists transactions and updates financial_summary"() {
        given: "a simple CSV with 3 transactions"
        String csv = '''id,amount,currency,category,description,date
TJ34,250.00,EUR,Zakupy,Monitor,2026-04-12
TJ35,-40.00,PLN,Jedzenie,Pizza,2026-04-12
TJ36,5000.00,PLN,Praca,Wypłata,2026-04-12
'''
        byte[] bytes = csv.getBytes('UTF-8')
        def multipart = new MockMultipartFile('file', 'transactions_upload.csv', 'text/csv', bytes)

        and: "current counts"
        def txBefore = transactionRepository.count()
        def summaryBefore = summaryRepo.findById('GLOBAL')
                .orElseGet({ new FinancialSummaryEntity(id: 'GLOBAL', totalBalance:0G, transactionCount:0L) })

        when: "we call upload controller as ADMIN"
        def auth = new UsernamePasswordAuthenticationToken('admin', null, [new SimpleGrantedAuthority('ROLE_ADMIN')])
        SecurityContextHolder.context.authentication = auth
        def resp = uploadController.uploadCsv(multipart, 'DB_TEST_USER')

        then: "response is OK and transactions persisted"
        resp.getStatusCode().value() == 200
        transactionRepository.count() == txBefore + 3

        and: "financial summary updated (wait for async projection)"
        // GlobalStatsProjector runs asynchronously — wait up to 10s for the projection to appear/update
        await().atMost(10, TimeUnit.SECONDS).until {
            summaryRepo.findById('GLOBAL').isPresent()
        }

        def summaryAfter = summaryRepo.findById('GLOBAL').orElse(null)
        summaryAfter != null
        summaryAfter.transactionCount == summaryBefore.transactionCount + 3
        // totalBalance may be computed (conversion may apply) - at least ensure it's present
        summaryAfter.totalBalance != null

        cleanup:
        SecurityContextHolder.clearContext()
    }

    def "upload CSV creates audit rows in transactions_aud"() {
        given: "a CSV with 2 transactions uploaded as admin"
        String id1 = "AUD-UP-${System.nanoTime()}-1"
        String id2 = "AUD-UP-${System.nanoTime()}-2"
        String csv = """id,amount,currency,category,description,date
${id1},120.00,PLN,Zakupy,Myszka,2026-04-12
${id2},-25.50,PLN,Jedzenie,Kawa,2026-04-12
"""
        def multipart = new MockMultipartFile('file', 'transactions_upload.csv', 'text/csv', csv.getBytes('UTF-8'))

        and: "current audit counters"
        def revinfoBefore = jdbcTemplate.queryForObject('SELECT COUNT(*) FROM revinfo', Long)

        when: "we execute the upload path"
        def auth = new UsernamePasswordAuthenticationToken('admin', null, [new SimpleGrantedAuthority('ROLE_ADMIN')])
        SecurityContextHolder.context.authentication = auth
        def resp = uploadController.uploadCsv(multipart, 'AUDIT_TEST_USER')

        then: "HTTP response is OK"
        resp.statusCode.value() == 200

        and: "manual audit rows were created for both uploaded transactions"
        def auditRows = jdbcTemplate.queryForList(
                '''
                SELECT original_id, revtype
                FROM transactions_aud
                WHERE original_id IN (?, ?)
                ORDER BY original_id, rev
                '''.stripIndent(),
                id1, id2
        )
        auditRows.size() == 2
        auditRows*.original_id as Set == [id1, id2] as Set
        auditRows*.revtype.every { it == 0 }

        and: "revinfo increased by at least one revision per inserted transaction"
        def revinfoAfter = jdbcTemplate.queryForObject('SELECT COUNT(*) FROM revinfo', Long)
        revinfoAfter >= revinfoBefore + 2

        cleanup:
        SecurityContextHolder.clearContext()
    }

    def "upload by regular ROLE_USER should be forbidden"() {
        given: "a sample CSV and a non-admin user"
        String csv = 'id,amount,currency,category,description,date\nX1,10.00,PLN,Test,OK,2026-04-12\n'
        byte[] bytes = csv.getBytes('UTF-8')
        def multipart = new MockMultipartFile('file', 'transactions_upload.csv', 'text/csv', bytes)

        when: "we call upload controller as ROLE_USER"
        def auth = new UsernamePasswordAuthenticationToken('user', null, [new SimpleGrantedAuthority('ROLE_USER')])
        SecurityContextHolder.context.authentication = auth

        uploadController.uploadCsv(multipart, 'SOME_USER')

        then: "access is denied"
        thrown(AccessDeniedException)

        cleanup:
        SecurityContextHolder.clearContext()
    }
}



