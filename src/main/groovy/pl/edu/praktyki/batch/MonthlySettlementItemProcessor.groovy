package pl.edu.praktyki.batch

import groovy.util.logging.Slf4j
import org.springframework.batch.item.ItemProcessor
import org.springframework.stereotype.Component
import pl.edu.praktyki.repository.TransactionEntity

/**
 * Lab89 – Spring Batch
 *
 * ItemProcessor: przekształca TransactionEntity → MonthlySettlementEntity (mapowanie 1:1).
 * Wyciąga miesiąc rozliczeniowy z daty transakcji i wystawia kwotę w PLN.
 *
 * Uwaga: Możesz tu dodać logikę filtrowania (return null = pomijamy transakcję w chunku).
 */
@Component
@Slf4j
class MonthlySettlementItemProcessor implements ItemProcessor<TransactionEntity, MonthlySettlementEntity> {

    @Override
    MonthlySettlementEntity process(TransactionEntity tx) throws Exception {
        // Wyznaczamy miesiąc rozliczeniowy: np. "2025-04"
        String month = tx.date ? tx.date.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM")) : "UNKNOWN"
        String category = tx.category ?: tx.categoryEntity?.name ?: "UNKNOWN"
        BigDecimal amount = tx.amountPLN ?: BigDecimal.ZERO

        log.debug("[BATCH] Processuję transakcję: id={}, miesiac={}, kategoria={}, kwota={} PLN",
                tx.dbId, month, category, amount)

        return new MonthlySettlementEntity(month, category, amount, 1)
    }
}

