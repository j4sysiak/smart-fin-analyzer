package pl.edu.praktyki.fraud
import pl.edu.praktyki.domain.TransactionDto

class NightTimeFraudRule implements FraudRule {
    @Override
    String check(TransactionDto tx) {
        // Upraszczamy: zakładamy, że mamy informację o godzinie
        // Jeśli opis zawiera słowo "NIGHT", traktujemy to jako operację nocną > 5000
        if (tx.description?.contains("NIGHT") && tx.amountPLN < -5000) {
            return "FRAUD: Duża transakcja w środku nocy"
        }
        return null
    }
}