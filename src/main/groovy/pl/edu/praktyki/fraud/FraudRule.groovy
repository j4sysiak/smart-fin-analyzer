package pl.edu.praktyki.fraud

import pl.edu.praktyki.domain.TransactionDto

interface FraudRule {
    /**
     * Zwraca treść ostrzeżenia, jeśli transakcja jest oszustwem.
     * Zwraca 'null', jeśli transakcja jest bezpieczna.
     */
    String check(TransactionDto tx)
}