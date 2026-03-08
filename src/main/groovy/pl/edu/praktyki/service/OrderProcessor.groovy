package pl.edu.praktyki.service

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import pl.edu.praktyki.strategy.OrderStrategy

@Service
class OrderProcessor {

    // Spring wstrzyknie WSZYSTKIE implementacje!
    // <--- wrzuca tu WSZYSTKIE Beany typu OrderStrategy!
    @Autowired List<OrderStrategy> strategies

    void process(String type, BigDecimal amount) {

        // Groovy: Znajdź strategię, która mówi 'true' na metodę supports
        def strategy = strategies.find { it.supports(type) }

        // Zabezpieczamy się przed nullem:
        if (strategy == null) {
            throw new IllegalArgumentException("Nieznany typ zamówienia: $type")
        }

        // tutaj już mamy konkretną strategię, więc możemy ją wywołać
        // magia Groovy: nie musimy rzutować, bo wiemy, że to OrderStrategy juz konkretnego typu:
        // np StandardOrderStrategy lub VipOrderStrategy
        strategy.process(amount)
    }
}
