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

    @ManyToOne(fetch = FetchType.LAZY) // LAZY jest kluczowe dla wydajności!
    @JoinColumn(name = "category_id")
    CategoryEntity categoryEntity

    // Pozostawiamy również kolumnę 'category' (stara kolumna String) dla kompatybilności z testami
    @Column(name = "category")
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

    // Setter/getter kompatybilnościowy: pozwala przypisać zarówno CategoryEntity jak i String do property 'category'
    void setCategory(Object c) {
        if (c instanceof CategoryEntity) {
            this.categoryEntity = (CategoryEntity) c
            this.category = c?.name
        } else {
            this.category = c?.toString()
        }
    }

    // Zwracamy albo encję CategoryEntity (jeśli dostępna), albo nazwę kategorii (String)
    Object getCategory() {
        return this.categoryEntity ?: this.category
    }

    /** Zwraca surową nazwę kategorii (String) z pola @Column, bez zwracania encji. Przydatne przy ręcznym zapisie JDBC. */
    String getCategoryName() {
        return this.category
    }
}