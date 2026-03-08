package pl.edu.praktyki.fraud
import pl.edu.praktyki.domain.Transaction

class AmountFraudRule implements FraudRule {
    @Override
    String check(Transaction tx) {
        if (tx.amountPLN && tx.amountPLN < -15000) {
            return "FRAUD: Podejrzanie wysoka kwota operacji (${tx.amountPLN} PLN)"
        }
        return null // Wszystko OK
    }
}