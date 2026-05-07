package pl.edu.praktyki.batch

import groovy.util.logging.Slf4j
import org.springframework.batch.item.Chunk
import org.springframework.batch.item.ItemWriter
import org.springframework.stereotype.Component

@Component
@Slf4j
class MonthlySettlementItemWriter implements ItemWriter<MonthlySettlementEntity> {

    private final MonthlySettlementRepository repository

    MonthlySettlementItemWriter(MonthlySettlementRepository repository) {
        this.repository = repository
    }

    @Override
    void write(Chunk<? extends MonthlySettlementEntity> chunk) throws Exception {
        if (chunk == null || chunk.isEmpty()) {
            return
        }

        repository.saveAll(chunk.items)
        log.debug('[BATCH] Zapisano {} rekordów monthly_settlement', chunk.size())
    }
}

