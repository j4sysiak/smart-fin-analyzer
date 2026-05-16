package pl.edu.praktyki.repository

import jakarta.persistence.*
import org.hibernate.envers.NotAudited
import org.springframework.data.annotation.CreatedBy
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.annotation.LastModifiedBy
import org.springframework.data.annotation.LastModifiedDate
import org.springframework.data.jpa.domain.support.AuditingEntityListener

import org.hibernate.annotations.SQLDelete
import org.hibernate.annotations.Where

import java.time.LocalDate
import java.time.LocalDateTime

@Entity
@Table(name = "transactions")

// Ta adnotacja rejestruje klasy nasłuchujące na cykl życia encji JPA dla TransactionEntity.
//  - AuditingEntityListener - listener Spring Data, który automatycznie uzupełnia pola audytowe, np.: - createdDate - lastModifiedDate - createdBy - lastModifiedBy
//  - TransactionAuditEntityListener - własny listener aplikacji. Zwykle służy do wykonania dodatkowej logiki przy zdarzeniach encji, np.: - przed zapisem - po odczycie - przed aktualizacją - przed usunięciem
// W praktyce oznacza to, że przy operacjach na TransactionEntity JPA wywoła metody oznaczone np.: - @PrePersist - @PreUpdate - @PostLoad - @PreRemove
@EntityListeners([AuditingEntityListener, TransactionAuditEntityListener])

@SQLDelete(sql = "UPDATE transactions SET deleted = true, deleted_at = CURRENT_TIMESTAMP WHERE db_id = ?")
@Where(clause = "deleted = false")
@Access(AccessType.FIELD)
class TransactionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "tx_generator")
    @SequenceGenerator(
            name = "tx_generator",
            sequenceName = "tx_seq",
            allocationSize = 50
    )
    private Long dbId

    private String originalId
    private LocalDate date
    private BigDecimal amount
    private String currency
    private BigDecimal amountPLN

    @NotAudited
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private CategoryEntity categoryEntity

    @Column(name = "category")
    private String category

    private String description

    @CreatedDate
    @Column(updatable = false)
    @NotAudited
    private LocalDateTime createdDate

    @LastModifiedDate
    @NotAudited
    private LocalDateTime lastModifiedDate

    @CreatedBy
    @Column(updatable = false)
    @NotAudited
    private String createdBy

    @LastModifiedBy
    @NotAudited
    private String lastModifiedBy

    @Column(name = "tags")
    @NotAudited
    private String tagsRaw

    @Transient
    @NotAudited
    private List<String> tags = []

    @Column(name = "owner_username")
    private String ownerUsername // Właściciel biznesowy rekordu

    @Column(name = "deleted", nullable = false)
    private boolean deleted = false

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt

    @Column(name = "deleted_by")
    private String deletedBy

    TransactionEntity() {}

    @PostLoad
    void syncTagsFromRaw() {
        this.tags = this.tagsRaw ?
                this.tagsRaw.split(',')
                        .collect { it.trim() }
                        .findAll { it }
                : []
    }

    @PrePersist
    @PreUpdate
    void syncTagsToRaw() {
        this.tagsRaw = this.tags ?
                this.tags.findAll { it != null && !it.trim().isEmpty() }
                        .join(',')
                : null
    }

    Long getDbId() { dbId }
    void setDbId(Long dbId) { this.dbId = dbId }

    String getOriginalId() { originalId }
    void setOriginalId(String originalId) { this.originalId = originalId }

    LocalDate getDate() { date }
    void setDate(LocalDate date) { this.date = date }

    BigDecimal getAmount() { amount }
    void setAmount(BigDecimal amount) { this.amount = amount }

    String getCurrency() { currency }
    void setCurrency(String currency) { this.currency = currency }

    BigDecimal getAmountPLN() { amountPLN }
    void setAmountPLN(BigDecimal amountPLN) { this.amountPLN = amountPLN }

    CategoryEntity getCategoryEntity() { categoryEntity }
    void setCategoryEntity(CategoryEntity categoryEntity) { this.categoryEntity = categoryEntity }

    String getCategory() { category }
    void setCategory(String category) { this.category = category }

    String getDescription() { description }
    void setDescription(String description) { this.description = description }

    LocalDateTime getCreatedDate() { createdDate }
    void setCreatedDate(LocalDateTime createdDate) { this.createdDate = createdDate }

    LocalDateTime getLastModifiedDate() { lastModifiedDate }
    void setLastModifiedDate(LocalDateTime lastModifiedDate) { this.lastModifiedDate = lastModifiedDate }

    String getCreatedBy() { createdBy }
    void setCreatedBy(String createdBy) { this.createdBy = createdBy }

    String getLastModifiedBy() { lastModifiedBy }
    void setLastModifiedBy(String lastModifiedBy) { this.lastModifiedBy = lastModifiedBy }

    String getTagsRaw() { tagsRaw }
    void setTagsRaw(String tagsRaw) { this.tagsRaw = tagsRaw }

    List<String> getTags() { tags }
    void setTags(List<String> tags) { this.tags = tags ?: [] }

    String getOwnerUsername() { ownerUsername }
    void setOwnerUsername(String ownerUsername) { this.ownerUsername = ownerUsername }

    @Transient
    String getCategoryName() { category }

    boolean isDeleted() { deleted }
    void setDeleted(boolean deleted) { this.deleted = deleted }

    LocalDateTime getDeletedAt() { deletedAt }
    void setDeletedAt(LocalDateTime deletedAt) { this.deletedAt = deletedAt }

    String getDeletedBy() { deletedBy }
    void setDeletedBy(String deletedBy) { this.deletedBy = deletedBy }

}