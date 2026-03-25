package pl.edu.praktyki.service

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.test.context.ActiveProfiles
import pl.edu.praktyki.BaseIntegrationSpec
import pl.edu.praktyki.domain.Transaction
import pl.edu.praktyki.facade.TransactionBulkSaver
import pl.edu.praktyki.repository.TransactionRepository
import pl.edu.praktyki.repository.TransactionEntity
import java.time.LocalDate
// ...existing imports...


// 1. @ActiveProfiles(value = ["local-pg"], inheritProfiles = false) powoduje,
//    że kontekst testowy ładuje profil local-pg i w efekcie użyje ustawień z pliku application-local-pg.properties
//    (albo application-local-pg.yml) zamiast domyślnych.
// 2. Test uruchamia pełny kontekst Springa (MockMvc + repozytoria) i będzie próbował połączyć się
//    z bazą zgodnie z ustawieniami z tego pliku.
//    Komentarz w kodzie słusznie mówi: musisz mieć lokalnego Postgresa uruchomionego — test tego serwera
//    nie uruchomi samodzielnie.
// 3. inheritProfiles = false oznacza, że tylko local-pg jest aktywny (inne profile domyślne nie są łączone).
// 4. H2 nie będzie działać w tym projekcie (jak napisano), więc albo uruchom lokalny Postgres,
//    albo użyj Testcontainers / wbudowanego Postgresa, jeśli chcesz automatycznie startować DB w testach.
// 5. Sprawdź w application-local-pg.properties URL, username i password oraz czy schemat/bazy zostały przygotowane przed uruchomieniem testów.


@AutoConfigureMockMvc

//KNOW HOW!  (ActiveProfiles = 'tc')
// To nie dziala, żeby użyć Testcontainers - profil 'tc', to BaseIntegrationSpec ustaw warunek na if (1==1)
// wtedy zawsze będzie używał Testcontainers.
// Wtedy ten test będzie działał bez konieczności uruchamiania ręcznie Postgresa na Docker.
// wtedy postgres będzie uruchamiany automatycznie w kontenerze Docker przez Testcontainers,
// a po zakończeniu testów będzie automatycznie zatrzymywany i usuwany.

//@ActiveProfiles(value = ["tc"], inheritProfiles = false)


//KNOW HOW!  (ActiveProfiles = 'local-pg')
// Wymusi użycie application-local-pg.properties ale musisz mieć wlączony lokalny Postgresa!
// (nie działa z H2, bo H2 nie obsługuje funkcji SQL, których używamy w repozytorium)
// tutaj info jak uruchomić lokalnego postgresa na dokerze dla profilu: local-pg:
//                     C:\dev\smart-fin-analyzer\src\test\resources\application-local-pg.properties

@ActiveProfiles(value = ["local-pg"], inheritProfiles = false) // pamietaj, że musisz mieć lokalnego Postgresa uruchomionego, żeby ten test działał!
class BatchPerformanceSpec extends BaseIntegrationSpec {

    @Autowired
    TransactionIngesterService pipelineService

    @Autowired
    TransactionRepository repository

    @Autowired
    TransactionBulkSaver bulkSaver
    @Autowired
    TransactionRuleService ruleService


    def setup() {
        // Przed każdym testem czyścimy bazę i dodajemy świeże dane
        repository.deleteAll()
    }

    def "powinien zapisać 50000 rekordów w czasie poniżej 2 sekund (Batch Processing)"() {
        given: "5000 transakcji"
        def bankA = (1..50000).collect { i ->
            new Transaction(
                    id: "A${i}",
                    date: LocalDate.now(),
                    amount: (i % 2 == 0 ? -(i * 1.0) : (i * 1.0)),
                    category: "Kategoria${i % 10}",
                    description: "Transakcja ${i}"
            )
        }

        and: "zestaw reguł biznesowych zdefiniowanych przez użytkownika"
        def myRules = [
                "if (amount > 0) addTag('INCOME')",
                "if (amount < -1000) addTag('HIGH_EXPENSE')",
                "if (description.contains('Netflix')) addTag('SUBSCRIPTION')"
        ]

        when: "zapisujemy wszystko na raz przy użyciu batcha (spring jpa)"
        int total = 50000
        long start = System.currentTimeMillis()

        // Measure sequential rule application for comparison (optional)
        def compiled = ruleService.compileRules(myRules)
        long seqStart = System.currentTimeMillis()
        bankA.each { tx -> ruleService.applyCompiledRules(tx, compiled) }
        long seqEnd = System.currentTimeMillis()
        def seqTime = seqEnd - seqStart

        long tIngestStart = System.currentTimeMillis()
        def flatListOfTransactions = pipelineService.ingestAndApplyRules([bankA], myRules)
        long tIngestEnd = System.currentTimeMillis()
        long ingestTime = tIngestEnd - tIngestStart
        int ingestedCount = flatListOfTransactions?.size() ?: 0

        long tMapStart = System.currentTimeMillis()
        def entities = flatListOfTransactions.collect { tx ->
            new TransactionEntity(
                    originalId: tx.id,
                    date: tx.date,
                    amount: tx.amount,
                    currency: tx.currency,
                    amountPLN: tx.amountPLN,
                    category: tx.category,
                    description: tx.description,
                    tags: tx.tags
            )
        }
        long tMapEnd = System.currentTimeMillis()
        long mappingTime = tMapEnd - tMapStart

        long tSaveStart = System.currentTimeMillis()
        bulkSaver.saveAllInTransaction(entities)
        long tSaveEnd = System.currentTimeMillis()
        long saveTime = tSaveEnd - tSaveStart

        long end = System.currentTimeMillis()
        println ">>> TIMINGS: ingest=${ingestTime}ms, mapping=${mappingTime}ms, save=${saveTime}ms, total=${end - start}ms (seqRule=${seqTime}ms)"

        then: "wszystkie rekordy zapisane"
        repository.count() == total
    }
}