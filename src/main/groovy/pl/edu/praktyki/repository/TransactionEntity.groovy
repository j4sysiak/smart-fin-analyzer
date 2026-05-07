package pl.edu.praktyki.repository

import jakarta.persistence.Access
import jakarta.persistence.AccessType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EntityListeners
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.PostPersist
import jakarta.persistence.PostLoad
import jakarta.persistence.PostRemove
import jakarta.persistence.PostUpdate
import jakarta.persistence.PrePersist
import jakarta.persistence.PreUpdate
import jakarta.persistence.SequenceGenerator
import jakarta.persistence.Table
import jakarta.persistence.Transient
import org.hibernate.envers.NotAudited
import org.springframework.data.annotation.CreatedBy
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.annotation.LastModifiedBy
import org.springframework.data.annotation.LastModifiedDate
import org.springframework.data.jpa.domain.support.AuditingEntityListener

import java.time.LocalDate
import java.time.LocalDateTime

@Entity
@Table(name = "transactions")
@EntityListeners([AuditingEntityListener, TransactionAuditEntityListener])
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

    TransactionEntity() {}

    @PostLoad
    void syncTagsFromRaw() {
        this.tags = this.tagsRaw ? this.tagsRaw.split(',').collect { it.trim() }.findAll { it } : []
    }

    @PrePersist
    @PreUpdate
    void syncTagsToRaw() {
        this.tagsRaw = this.tags ? this.tags.findAll { it != null && !it.trim().isEmpty() }.join(',') : null
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

    @Transient
    String getCategoryName() { category }
}