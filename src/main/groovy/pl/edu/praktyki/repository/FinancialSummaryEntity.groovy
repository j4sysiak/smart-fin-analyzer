package pl.edu.praktyki.repository

import jakarta.persistence.*

@Entity
@Table(name = "financial_summary")
class FinancialSummaryEntity {
    @Id
    String id = "GLOBAL" // Mamy tylko jeden wiersz z globalnym bilansem

    BigDecimal totalBalance = 0.0
    Long transactionCount = 0
}