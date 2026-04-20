package pl.edu.praktyki.repository

import jakarta.persistence.*
import org.springframework.data.annotation.*
import org.springframework.data.jpa.domain.support.AuditingEntityListener

import java.time.LocalDate
import java.time.LocalDateTime

@Entity
@EntityListeners(AuditingEntityListener.class) // <-- KLUCZOWE dla automatycznego zarządzania polami audytu
@Table(name = "transactions")
class TransactionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "tx_generator")
    @SequenceGenerator(
            name = "tx_generator",
            sequenceName = "tx_seq",
            allocationSize = 50 // <--- MUSI SIĘ ZGADZAĆ Z INCREMENT BY W SQL
    )
    Long dbId

    String originalId // Zamiast 'id', żeby nie myliło się z kluczem w bazie
    LocalDate date
    BigDecimal amount
    String currency
    BigDecimal amountPLN
    String category
    String description

    @CreatedDate
    @Column(updatable = false)
    LocalDateTime createdDate

    @LastModifiedDate
    LocalDateTime lastModifiedDate

    @CreatedBy
    @Column(updatable = false)
    String createdBy

    @LastModifiedBy
    String lastModifiedBy

    @ElementCollection(fetch = FetchType.EAGER)
    List<String> tags = []

    // Wymagany przez Hibernate pusty konstruktor
    TransactionEntity() {}
}