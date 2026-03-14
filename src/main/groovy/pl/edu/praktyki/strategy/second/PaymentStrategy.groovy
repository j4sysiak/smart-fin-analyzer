package pl.edu.praktyki.strategy.second

import org.springframework.stereotype.Component

@Component
class PaymentStrategy implements TransactionStrategy {

    boolean supports(String type) {
        type == "PAYMENT"
    }

    void execute(BigDecimal amount) {
        println "Płatność kartą: $amount PLN"
    }
}