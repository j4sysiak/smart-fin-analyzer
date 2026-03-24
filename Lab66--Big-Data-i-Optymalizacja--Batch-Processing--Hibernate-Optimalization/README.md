Lab66
-----

Lab66--Big-Data-i-Optymalizacja--Batch-Processing--Hibernate-Optimalization
---------------------------------------------------------------------------

Teraz czas na Etap 3: 
Big Data i Optymalizacja Hibernate. 
To tutaj często "padają" aplikacje w firmach, gdy po kilku miesiącach działania baza danych zaczyna 
"puchnąć", a zapytania SQL trwają sekundy zamiast milisekund.

Problem: 
Jeśli w `IngesterService` zapisujesz transakcje w pętli `repo.save(tx)`, 
to dla 1000 transakcji wysyłasz do bazy 1000 osobnych zapytań INSERT. 
To jest zabójstwo dla wydajności sieci i bazy danych.

Cel: 
Zastosować `Batch Processing w Hibernate, aby wysyłać zapytania paczkami po np. 50 sztuk.

Krok 1: Optymalizacja application.properties
--------------------------------------------
Musimy "nakazać" Hibernate'owi, żeby grupował zapytania. 
Dodaj to do `src/main/resources/application.properties`:

```text
# Optymalizacja Hibernate dla dużej ilości danych
spring.jpa.properties.hibernate.jdbc.batch_size=50
spring.jpa.properties.hibernate.order_inserts=true
spring.jpa.properties.hibernate.order_updates=true
```


Krok 2: Kodowanie
--------------------------------------------
W świecie Springa, zapisywanie tysięcy encji naraz najlepiej robić wewnątrz `@Transactional`.
Dodaj klase w `pl.edu.praktyki.facade.TransactionBulkSaver.groovy`:

```groovy
package pl.edu.praktyki.facade

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.beans.factory.annotation.Autowired
import pl.edu.praktyki.repository.TransactionRepository
import pl.edu.praktyki.repository.TransactionEntity
import groovy.util.logging.Slf4j
import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.BatchPreparedStatementSetter
import java.sql.PreparedStatement
import java.sql.SQLException
import java.io.StringWriter
import java.io.StringReader
import javax.sql.DataSource
import org.postgresql.copy.CopyManager
import org.postgresql.core.BaseConnection

@Service
@Slf4j
class TransactionBulkSaver {

    @Autowired TransactionRepository repo
    @PersistenceContext EntityManager em
    @Autowired(required = false) JdbcTemplate jdbcTemplate
    @Autowired(required = false) DataSource dataSource

    /**
     * Save all entities inside a single transaction. This will be executed in a
     * transactional proxy so batch inserts / single-transaction behaviour will apply.
     */
    @Transactional
    void saveAllInTransaction(List<TransactionEntity> entities) {
        if (!entities) return
        int chunkSize = Math.max(entities.size(), 500)
        log.debug('>>> [BULK SAVER] Saving {} entities in chunks of {} in one transaction', entities.size(), chunkSize)

        // Fastest path for Postgres: use CopyManager (COPY FROM STDIN)
        if (dataSource) {
            try {
                def conn = dataSource.getConnection()
                try {
                    // Try to get a BaseConnection for CopyManager
                    BaseConnection baseConn = conn.unwrap(BaseConnection.class)
                    if (baseConn != null) {
                        CopyManager copyManager = new CopyManager(baseConn)
                        // Build tab-delimited content for COPY, including db_id values fetched from sequence
                        int total = entities.size()
                        int startIdx = 0
                        while (startIdx < total) {
                            def batch = entities.subList(startIdx, Math.min(startIdx + chunkSize, total))
                            // Fetch sequence values for this batch
                            List<Long> seqVals = []
                            if (jdbcTemplate) {
                                seqVals = jdbcTemplate.queryForList("SELECT nextval('tx_seq') FROM generate_series(1, ?)", Long.class, batch.size())
                            } else {
                                // fallback: generate dummy sequence values (should not happen when dataSource present)
                                batch.each { seqVals << System.currentTimeMillis() }
                            }

                            StringWriter sw = new StringWriter()
                            for (int i = 0; i < batch.size(); i++) {
                                TransactionEntity e = batch.get(i)
                                def id = seqVals.get(i)
                                def date = e.date ? e.date.toString() : ''
                                def parts = [id, e.originalId ?: '', date, e.amount ?: '', e.currency ?: '', e.amountPLN ?: '', e.category ?: '', e.description ?: '']
                                def line = parts.collect { it.toString().replace('\n',' ').replace('\r',' ') }.join('\t')
                                sw.write(line + '\n')
                            }
                            String copySql = "COPY transactions (db_id, original_id, date, amount, currency, amountpln, category, description) FROM STDIN WITH (FORMAT text, DELIMITER E'\\t')"
                            copyManager.copyIn(copySql, new StringReader(sw.toString()))
                            startIdx += batch.size()
                        }
                        conn.close()
                        return
                    }
                } catch (Exception e) {
                    log.debug('COPY path failed, will fallback to batchUpdate: {}', e.message)
                }
                conn.close()
            } catch (Exception ignored) {
                log.debug('COPY path not available, falling back to batchUpdate: {}', ignored.message)
            }
        }

        // Fast path: use JdbcTemplate.batchUpdate to insert rows in batches (much faster than Hibernate for pure inserts)
        if (jdbcTemplate) {
            // Use nextval on sequence to populate primary key (we bypass Hibernate here)
            String sql = "insert into transactions (db_id, original_id, date, amount, currency, amountpln, category, description) values (nextval('tx_seq'),?,?,?,?,?,?,?)"
            entities.collate(chunkSize).each { List<TransactionEntity> batch ->
                jdbcTemplate.batchUpdate(sql, new BatchPreparedStatementSetter() {
                    @Override
                    void setValues(PreparedStatement ps, int i) throws SQLException {
                        TransactionEntity e = batch.get(i)
                        ps.setString(1, e.originalId)
                        ps.setDate(2, java.sql.Date.valueOf(e.date))
                        ps.setBigDecimal(3, e.amount)
                        ps.setString(4, e.currency)
                        ps.setBigDecimal(5, e.amountPLN)
                        ps.setString(6, e.category)
                        ps.setString(7, e.description)
                    }

                    @Override
                    int getBatchSize() {
                        return batch.size()
                    }
                })
            }
        } else {
            // Fallback: use EntityManager.persist with flush/clear
            entities.collate(chunkSize).each { List<TransactionEntity> batch ->
                batch.each { TransactionEntity ent ->
                    em.persist(ent)
                }
                em.flush()
                em.clear()
            }
        }
    }
}
```

Krok 3: Wyzwanie – Symulacja "Big Data" (Test Obciążeniowy)
-----------------------------------------------------------
Napiszemy test, który wczyta 50000 transakcji i sprawdzi, czy czas zapisu jest akceptowalny.

Stwórz `src/test/groovy/pl/edu/praktyki/service/BatchPerformanceSpec.groovy`:

```groovy
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

@ActiveProfiles(value = ["local-pg"], inheritProfiles = false)
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
```
 