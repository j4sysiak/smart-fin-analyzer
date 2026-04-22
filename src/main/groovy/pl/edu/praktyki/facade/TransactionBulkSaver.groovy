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
                                // category may be a String (old code/tests) or a CategoryEntity (new model).
                                // Normalize to category name for the text COPY path.
                                def categoryName = (e.category instanceof String) ? e.category : (e.category?.name ?: '')
                                def parts = [id, e.originalId ?: '', date, e.amount ?: '', e.currency ?: '', e.amountPLN ?: '', categoryName, e.description ?: '']
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

                        // e.date may be null. Avoid ambiguous overload of java.sql.Date.valueOf(null).
                        if (e.date == null) {
                            ps.setNull(2, java.sql.Types.DATE)
                        } else if (e.date instanceof java.time.LocalDate) {
                            ps.setDate(2, java.sql.Date.valueOf((java.time.LocalDate) e.date))
                        } else {
                            // fallback: convert to string and parse
                            ps.setDate(2, java.sql.Date.valueOf(e.date.toString()))
                        }

                        ps.setBigDecimal(3, e.amount)
                        ps.setString(4, e.currency)
                        ps.setBigDecimal(5, e.amountPLN)
                        // category may be stored as plain String in some places or as CategoryEntity in the new model.
                        if (e.category == null) {
                            ps.setNull(6, java.sql.Types.VARCHAR)
                        } else if (e.category instanceof String) {
                            ps.setString(6, (String) e.category)
                        } else {
                            // assume it's a CategoryEntity or similar; use its name
                            ps.setString(6, e.category?.name)
                        }
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

