package pl.edu.praktyki.strategy.second

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service

@Service
class TransactionProcessor {

    // Spring automatycznie zbierze tu wszystkie Beany, które implementują TransactionStrategy
    @Autowired
    List<TransactionStrategy> strategies

    void process(String type, BigDecimal amount) {

        // Groovy: Znajdź strategię, która mówi 'true' na metodę supports
        def strategy = strategies.find { it.supports(type) }

        if (!strategy) throw new IllegalArgumentException("Brak strategii dla: $type")

        strategy.execute(amount)
    }
}
