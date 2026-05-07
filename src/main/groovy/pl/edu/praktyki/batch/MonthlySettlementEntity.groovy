package pl.edu.praktyki.batch

import jakarta.persistence.*

/**
 * Lab89 – Spring Batch
 *
 * Encja reprezentująca jeden rekord miesięcznego rozliczenia.
 * Batch job tworzy jeden wiersz dla każdej przetworzonej transakcji.
 * Dla prawdziwej agregacji (GROUP BY kategoria+miesiąc) dodaj drugi Step lub użyj SQL po zakończeniu joba.
 */
@Entity
@Table(name = "monthly_settlement")
class MonthlySettlementEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id

    /** Miesiąc w formacie "yyyy-MM", np. "2025-04" */
    @Column(name = "settlement_month", nullable = false)
    String settlementMonth

    /** Nazwa kategorii transakcji, np. "FOOD", "TRANSPORT" */
    @Column(nullable = false)
    String category

    /** Kwota transakcji w PLN */
    @Column(name = "total_amount", nullable = false)
    BigDecimal totalAmount

    /** Liczba transakcji w tym rekordzie (zawsze 1 przy mapowaniu 1:1 z transakcją) */
    @Column(name = "transaction_count", nullable = false)
    Integer transactionCount

    MonthlySettlementEntity() {}

    MonthlySettlementEntity(String settlementMonth, String category, BigDecimal totalAmount, Integer transactionCount) {
        this.settlementMonth = settlementMonth
        this.category = category
        this.totalAmount = totalAmount
        this.transactionCount = transactionCount
    }

    @Override
    String toString() {
        "MonthlySettlement[month=$settlementMonth, cat=$category, amt=$totalAmount, count=$transactionCount]"
    }
}

