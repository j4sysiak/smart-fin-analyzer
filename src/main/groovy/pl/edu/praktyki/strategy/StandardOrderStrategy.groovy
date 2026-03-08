package pl.edu.praktyki.strategy
import org.springframework.stereotype.Component

@Component
class StandardOrderStrategy implements OrderStrategy {

    boolean supports(String type) {
        type == "STANDARD"
    }

    void process(BigDecimal amount) {
        println "Standardowe przetwarzanie kwoty: $amount"
    }
}