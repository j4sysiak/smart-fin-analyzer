package pl.edu.praktyki.repository

import jakarta.persistence.*
import java.time.LocalDate

@Entity
@Table(name = "transactions")
class TransactionEntity {

    @Id
    // Use SEQUENCE instead of IDENTITY so Hibernate can batch inserts.
    @SequenceGenerator(name = "tx_seq", sequenceName = "tx_seq", allocationSize = 50)
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "tx_seq")
    Long dbId

    String originalId // Zamiast 'id', żeby nie myliło się z kluczem w bazie
    LocalDate date
    BigDecimal amount
    String currency
    BigDecimal amountPLN
    String category
    String description

    @ElementCollection(fetch = FetchType.EAGER)
    List<String> tags = []

    // Wymagany przez Hibernate pusty konstruktor
    TransactionEntity() {}
}