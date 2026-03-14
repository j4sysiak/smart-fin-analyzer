package pl.edu.praktyki.strategy.second

import org.springframework.stereotype.Component

@Component
class TransferStrategy implements TransactionStrategy {

    boolean supports(String type) {
        type == "TRANSFER"
    }

    void execute(BigDecimal amount) {
        println "Przelew: $amount PLN"
    }
}