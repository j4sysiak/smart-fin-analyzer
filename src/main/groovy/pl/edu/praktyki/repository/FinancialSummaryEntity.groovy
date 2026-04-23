package pl.edu.praktyki.repository

import jakarta.persistence.*

@Entity
@Table(name = "financial_summary")
class FinancialSummaryEntity {
    @Id
    String id = "GLOBAL" // Mamy tylko jeden wiersz z globalnym bilansem

    BigDecimal totalBalance = 0.0
    Long transactionCount = 0

    @Version // <--- MAGIA: Automatyczna ochrona przed nadpisaniem danych
    Long version

    @Override
    boolean equals(Object o) {
        if (this.is(o)) return true
        if (!(o instanceof FinancialSummaryEntity)) return false
        FinancialSummaryEntity other = (FinancialSummaryEntity) o
        return (id != null ? id.equals(other.id) : other.id == null)
    }

    @Override
    int hashCode() {
        id != null ? id.hashCode() : 0
    }

    @Override
    String toString() {
        "FinancialSummaryEntity(id=${id}, totalBalance=${totalBalance}, transactionCount=${transactionCount}, version=${version})"
    }
}