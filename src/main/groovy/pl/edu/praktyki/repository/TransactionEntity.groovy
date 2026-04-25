package pl.edu.praktyki.repository

import jakarta.persistence.*
import org.hibernate.Hibernate
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
    // UWAGA: używamy this.@field (bezpośredni dostęp do pola) żeby uniknąć rekurencji Groovy property dispatch
    void setCategory(Object c) {
        if (c instanceof CategoryEntity) {
            this.@categoryEntity = (CategoryEntity) c
            this.@category = c?.name   // bezpośredni zapis do pola, bez woławnia setCategory() ponownie
        } else {
            this.@category = c?.toString()  // bezpośredni zapis do pola
        }
    }

    // Zwracamy albo encję CategoryEntity (jeśli dostępna i załadowana), albo nazwę kategorii (String)
    // UWAGA: NIE używamy operator ?: na lazy proxy Hibernate — Groovy wywołałby getMetaClass() na proxy
    // co triggeruje LazyInitializationException poza sesją JPA.
    Object getCategory() {
        if (this.@categoryEntity == null) return this.@category
        if (!Hibernate.isInitialized(this.@categoryEntity)) return this.@category
        return this.@categoryEntity
    }

    /** Zwraca surową nazwę kategorii (String) z pola @Column, bez zwracania encji. Przydatne przy ręcznym zapisie JDBC. */
    // UWAGA: używamy this.@category (bezpośredni dostęp do pola) żeby uniknąć wywołania getCategory() przez Groovy
    String getCategoryName() {
        return this.@category
    }
}