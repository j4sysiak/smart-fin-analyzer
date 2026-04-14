package pl.edu.praktyki.integration

import pl.edu.praktyki.BaseIntegrationSpec
import pl.edu.praktyki.web.UploadController
import pl.edu.praktyki.repository.TransactionRepository
import pl.edu.praktyki.repository.FinancialSummaryRepository
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.mock.web.MockMultipartFile
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority

class UploadIntegrationSpec extends BaseIntegrationSpec {

    @Autowired
    UploadController uploadController

    @Autowired
    TransactionRepository transactionRepository

    @Autowired
    FinancialSummaryRepository summaryRepo

    def "upload CSV increases financial_summary.transactionCount by number of transactions"() {
        given: "a simple CSV with 3 transactions"
        String csv = '''id,amount,currency,category,description,date
TJ34,250.00,EUR,Zakupy,Monitor,2026-04-12
TJ35,-40.00,PLN,Jedzenie,Pizza,2026-04-12
TJ36,5000.00,PLN,Praca,Wypłata,2026-04-12
'''
        byte[] bytes = csv.getBytes('UTF-8')
        def multipart = new MockMultipartFile('file', 'transactions_upload.csv', 'text/csv', bytes)

        and: "current transactionCount"
        def before = summaryRepo.findById('GLOBAL').orElseGet({ new pl.edu.praktyki.repository.FinancialSummaryEntity(id: 'GLOBAL', totalBalance:0G, transactionCount:0L) }).transactionCount

        when: "we call upload controller"
        // Ustawiamy kontekst bezpieczeństwa, bo metoda kontrolera jest zabezpieczona @PreAuthorize
        def auth = new UsernamePasswordAuthenticationToken('admin', null, [new SimpleGrantedAuthority('ROLE_ADMIN')])
        SecurityContextHolder.context.authentication = auth
        def resp = uploadController.uploadCsv(multipart, 'TEST_USER')

        then: "response is OK"
        resp.statusCodeValue == 200

        and: "financial summary transactionCount increased by 3"
        def after = summaryRepo.findById('GLOBAL').orElseGet({ new pl.edu.praktyki.repository.FinancialSummaryEntity(id: 'GLOBAL', totalBalance:0G, transactionCount:0L) }).transactionCount
        after == before + 3

        cleanup:
        SecurityContextHolder.clearContext()
    }
}

