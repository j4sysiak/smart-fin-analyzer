package pl.edu.praktyki.facade

import groovy.util.logging.Slf4j
import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import org.postgresql.copy.CopyManager
import org.postgresql.core.BaseConnection
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.BatchPreparedStatementSetter
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import pl.edu.praktyki.repository.TransactionEntity
import pl.edu.praktyki.repository.TransactionRepository

import javax.sql.DataSource
import java.sql.PreparedStatement
import java.sql.SQLException
import java.sql.Timestamp
import java.time.LocalDateTime

@Service
@Slf4j
class TransactionBulkSaver {

    @Autowired TransactionRepository repo
    @PersistenceContext EntityManager em
    @Autowired(required = false) JdbcTemplate jdbcTemplate
    @Autowired(required = false) DataSource dataSource


    // standardowym Spring Data JPA, użycie repository.saveAll() przy strategii klucza głównego GenerationType.IDENTITY
    // całkowicie wyłącza JDBC Batching.
    // Hibernate musi wysłać osobnego INSERTa i odczekać na wygenerowane ID dla każdego rekordu, co przy milionie wierszy zabija aplikację.
    // To rozwiązanie – czyli ominięcie ORM-a, zrzucenie maski i uderzenie bezpośrednio
    // w natywne API PostgreSQL (CopyManager / COPY FROM STDIN),
    // a jako fallback użycie JdbcTemplate.batchUpdate – to absolutnie najszybszy znany ludzkości sposób ładowania
    // danych do bazy relacyjnej w środowisku JVM.


    /**
     * Save all entities inside a single transaction. This will be executed in a
     * transactional proxy so batch inserts / single-transaction behaviour will apply.
     */
    // Adnotacje takie jak @Async czy @Transactional tworzą "opakowanie" wokół Twojej klasy.
    /** Pobiera nazwę bieżącego użytkownika z SecurityContextHolder lub zwraca 'SYSTEM' dla tasków w tle. */
    private static String getAuditor() {
        try {
            def auth = SecurityContextHolder.context.authentication
            if (auth == null || !auth.isAuthenticated() || auth.principal == 'anonymousUser') return 'SYSTEM'
            return auth.name
        } catch (Exception ignored) {
            return 'SYSTEM'
        }
    }

    @Transactional
    void saveAllInTransaction(List<TransactionEntity> entities) {
        if (!entities) return
        int chunkSize = Math.max(entities.size(), 500)
        log.info('>>> [BULK SAVER] Saving {} entities in chunks of {} in one transaction', entities.size(), chunkSize)

        // Pobieramy audytor i timestamp raz dla całego batcha
        final String auditor = getAuditor()
        final LocalDateTime now = LocalDateTime.now()
        log.info('>>> [BULK SAVER] auditor={}, now={}', auditor, now)

        // Fastest path for Postgres: use CopyManager (COPY FROM STDIN)
        if (dataSource) {
            def conn = null
            boolean copySucceeded = false
            try {
                conn = dataSource.getConnection()
                BaseConnection baseConn = conn.unwrap(BaseConnection.class)
                if (baseConn != null) {
                    log.info('>>> [BULK SAVER] Używam ścieżki COPY FROM STDIN')
                    CopyManager copyManager = new CopyManager(baseConn)
                    int total = entities.size()
                    int startIdx = 0
                    while (startIdx < total) {
                        def batch = entities.subList(startIdx, Math.min(startIdx + chunkSize, total))
                        List<Long> seqVals = []
                        if (jdbcTemplate) {
                            seqVals = jdbcTemplate.queryForList("SELECT nextval('tx_seq') FROM generate_series(1, ?)", Long.class, batch.size())
                        } else {
                            batch.each { seqVals << System.currentTimeMillis() }
                        }

                        StringWriter sw = new StringWriter()
                        // PostgreSQL COPY TEXT format wymaga spacji jako separatora w TIMESTAMP (nie 'T' jak w ISO 8601)
                        def nowStr = now.toString().replace('T', ' ')
                        for (int i = 0; i < batch.size(); i++) {
                            TransactionEntity e = batch.get(i)
                            def id = seqVals.get(i)
                            def date = e.date ? e.date.toString() : '\\N'
                            def cat = e.categoryEntity
                            def categoryName = cat?.name ?: (e.categoryName ?: '')
                            def categoryId = cat?.id != null ? cat.id.toString() : '\\N'
                            def parts = [id, e.originalId ?: '', date,
                                         e.amount != null ? e.amount : '\\N',
                                         e.currency ?: '',
                                         e.amountPLN != null ? e.amountPLN : '\\N',
                                         categoryName, e.description ?: '',
                                         nowStr, nowStr, auditor, auditor, categoryId]
                            def line = parts.collect { it.toString().replace('\t', ' ').replace('\n', ' ').replace('\r', ' ') }.join('\t')
                            sw.write(line + '\n')
                        }
                        // Dodano kolumny audytowe i category_id
                        String copySql = "COPY transactions (db_id, original_id, date, amount, currency, amountpln, category, description, created_date, last_modified_date, created_by, last_modified_by, category_id) FROM STDIN WITH (FORMAT text, DELIMITER E'\\t', NULL '\\N')"
                        log.info('>>> [BULK SAVER] COPY SQL: {}', copySql)
                        if (sw.toString()) {
                            log.info('>>> [BULK SAVER] Przykładowy wiersz TSV: {}', sw.toString().split('\n')[0])
                        }
                        copyManager.copyIn(copySql, new StringReader(sw.toString()))
                        startIdx += batch.size()
                    }
                    copySucceeded = true
                    log.info('>>> [BULK SAVER] COPY zakończony sukcesem')
                }
            } catch (Exception e) {
                log.warn('>>> [BULK SAVER] COPY path failed ({}): {} — fallback do batchUpdate', e.class.simpleName, e.message, e)
            } finally {
                try { conn?.close() } catch (ignored) {}
            }
            if (copySucceeded) return
        }

        // Fast path: use JdbcTemplate.batchUpdate
        if (jdbcTemplate) {
            log.info('>>> [BULK SAVER] Używam ścieżki batchUpdate, auditor={}, now={}', auditor, now)
            // Dodano kolumny audytowe i category_id
            String sql = "insert into transactions (db_id, original_id, date, amount, currency, amountpln, category, description, created_date, last_modified_date, created_by, last_modified_by, category_id) values (nextval('tx_seq'),?,?,?,?,?,?,?,?,?,?,?,?)"
            final String _auditor = auditor
            final LocalDateTime _now = now
            entities.collate(chunkSize).each { List<TransactionEntity> batch ->
                jdbcTemplate.batchUpdate(sql, new BatchPreparedStatementSetter() {
                    @Override
                    void setValues(PreparedStatement ps, int i) throws SQLException {
                        TransactionEntity e = batch.get(i)
                        ps.setString(1, e.originalId)

                        if (e.date == null) {
                            ps.setNull(2, java.sql.Types.DATE)
                        } else if (e.date instanceof java.time.LocalDate) {
                            ps.setDate(2, java.sql.Date.valueOf((java.time.LocalDate) e.date))
                        } else {
                            ps.setDate(2, java.sql.Date.valueOf(e.date.toString()))
                        }

                        ps.setBigDecimal(3, e.amount)
                        ps.setString(4, e.currency)
                        ps.setBigDecimal(5, e.amountPLN)

                        def cat = e.categoryEntity
                        def catStr = e.categoryName
                        if (cat != null) {
                            ps.setString(6, cat.name)
                        } else if (catStr != null) {
                            ps.setString(6, catStr)
                        } else {
                            ps.setNull(6, java.sql.Types.VARCHAR)
                        }
                        ps.setString(7, e.description)

                        // Kolumny audytowe – wypełniane ręcznie (omijamy JPA Auditing)
                        ps.setTimestamp(8, Timestamp.valueOf(_now))
                        ps.setTimestamp(9, Timestamp.valueOf(_now))
                        ps.setString(10, _auditor)
                        ps.setString(11, _auditor)

                        // FK do kategorii
                        if (cat?.id != null) {
                            ps.setLong(12, cat.id)
                        } else {
                            ps.setNull(12, java.sql.Types.BIGINT)
                        }
                    }

                    @Override
                    int getBatchSize() {
                        return batch.size()
                    }
                })
            }
            log.info('>>> [BULK SAVER] batchUpdate zakończony sukcesem')
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

