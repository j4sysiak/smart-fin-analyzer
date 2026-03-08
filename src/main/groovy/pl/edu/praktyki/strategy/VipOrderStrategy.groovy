package pl.edu.praktyki.strategy
import org.springframework.stereotype.Component

@Component
class VipOrderStrategy implements OrderStrategy {

    boolean supports(String type) {
        type == "VIP"
    }

    void process(BigDecimal amount) {
        println "VIP otrzymał zniżkę 20%: ${amount * 0.8}"
    }
}