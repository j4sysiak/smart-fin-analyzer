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

// KLUCZOWE IMPORTY DLA ASYNCHRONICZNOŚCI
import static org.awaitility.Awaitility.await
import java.util.concurrent.TimeUnit

class UploadIntegrationSpec extends BaseIntegrationSpec {

    @Autowired
    UploadController uploadController

    @Autowired
    TransactionRepository transactionRepository

    @Autowired
    FinancialSummaryRepository summaryRepo

    def "upload CSV increases financial_summary.transactionCount by number of transactions"() {
        given: "prosty plik CSV z 3 transakcjami"
        String csv = '''id,amount,currency,category,description,date
TJ34,250.00,EUR,Zakupy,Monitor,2026-04-12
TJ35,-40.00,PLN,Jedzenie,Pizza,2026-04-12
TJ36,5000.00,PLN,Praca,Wypłata,2026-04-12
'''
        byte[] bytes = csv.getBytes('UTF-8')
        def multipart = new MockMultipartFile('file', 'transactions_upload.csv', 'text/csv', bytes)

        and: "pobieramy stan licznika przed testem"
        def before = summaryRepo.findById('GLOBAL')
                .map { it.transactionCount }
                .orElse(0L)

        when: "wywołujemy kontroler z uprawnieniami administratora"
        def auth = new UsernamePasswordAuthenticationToken('admin', null, [new SimpleGrantedAuthority('ROLE_ADMIN')])
        SecurityContextHolder.context.authentication = auth

        // Bezpośrednie wywołanie metody kontrolera (ponieważ jest @Autowired, zadziała AOP i Eventy)
        def resp = uploadController.uploadCsv(multipart, 'TEST_USER')

        then: "Czekamy asynchronicznie (max 10s), aż GlobalStatsProjector zaktualizuje bazę"
        await().atMost(10, TimeUnit.SECONDS).until {
            def currentCount = summaryRepo.findById("GLOBAL")
                    .map { it.transactionCount }
                    .orElse(0L)
            return currentCount == before + 3
        }

        and: "odpowiedź kontrolera ma status 200 OK"
        // W Spring Boot 3 używamy .statusCode.value() zamiast statusCodeValue
        resp.statusCode.value() == 200

        and: "licznik końcowy jest poprawny"
        summaryRepo.findById('GLOBAL').get().transactionCount == before + 3

        cleanup:
        SecurityContextHolder.clearContext()
    }
}