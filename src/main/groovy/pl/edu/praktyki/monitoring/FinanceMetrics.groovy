package pl.edu.praktyki.monitoring

import io.micrometer.core.instrument.MeterRegistry
import org.springframework.stereotype.Component
import java.util.concurrent.atomic.AtomicReference

@Component
class FinanceMetrics {

    // 1. Licznik transakcji (Counter)
    private final io.micrometer.core.instrument.Counter transactionCounter

    // Używamy AtomicReference dla bezpieczeństwa wątkowego z BigDecimal
    private final AtomicReference<BigDecimal> totalBalanceGauge = new AtomicReference<>(BigDecimal.ZERO)

    // 2. Średnia wartość (Gauge)
    //private final AtomicReference<Double> averageValue = new AtomicReference<>(0.0)

    FinanceMetrics(MeterRegistry registry) {
        // Rejestracja licznika
        this.transactionCounter = registry.counter("smartfin.transactions.processed")

        // Rejestracja Gauge'a (wskaźnika) - Spring będzie pytał ten obiekt o wartość
        // Pierwszy argument: nazwa
        // Drugi argument: obiekt, który trzyma wartość
        // Trzeci argument: funkcja, która wyciąga wartość (doubleValue)
        // registry.gauge("smartfin.transactions.total.balance", totalBalanceGauge, { ref -> ref.get().doubleValue() })

        // Gauge dla bilansu (smartfin.transactions.total.balance)
        // Spring automatycznie wyciągnie wartość z AtomicReference
        registry.gauge("smartfin.transactions.total.balance", totalBalanceGauge, { ref -> ref.get().doubleValue() })
    }

    void recordTransaction(BigDecimal amount) {
        transactionCounter.increment()
    }

    /**
     * Increment transaction counter by n (fast path for bulk operations).
     */
    void recordTransactions(int n) {
        if (n <= 0) return
        transactionCounter.increment((double) n)
    }

    void updateBalance(BigDecimal newBalance) {
        totalBalanceGauge.set(newBalance)
    }
}