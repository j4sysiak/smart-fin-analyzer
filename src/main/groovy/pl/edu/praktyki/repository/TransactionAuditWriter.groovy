package pl.edu.praktyki.repository

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component

@Component
class TransactionAuditWriter {

    private final JdbcTemplate jdbcTemplate

    TransactionAuditWriter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate
    }

    void writeRevision(TransactionEntity entity, int revType) {
        Long rev = jdbcTemplate.queryForObject(
                "INSERT INTO revinfo (rev, revtstmp) VALUES (nextval('revinfo_seq'), ?) RETURNING rev",
                Long,
                System.currentTimeMillis()
        )

        jdbcTemplate.update(
                '''
                INSERT INTO transactions_aud (
                    db_id, rev, revtype,
                    original_id, date, amount, currency, amountpln,
                    category, description, category_id
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                '''.stripIndent(),
                entity.dbId,
                rev,
                revType,
                entity.originalId,
                entity.date,
                entity.amount,
                entity.currency,
                entity.amountPLN,
                entity.category,
                entity.description,
                entity.categoryEntity?.id
        )
    }
}

