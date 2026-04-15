package pl.edu.praktyki.repository

import org.springframework.data.jpa.domain.Specification

class TransactionSpecifications {

    static Specification<TransactionEntity> hasCategory(String category) {

        /*
        to wywołanie metody CriteriaBuildera.
        Krótko:
        -> — lambda/closure (implementuje Specification / toPredicate).
                cb.greaterThanOrEqualTo(root.get("amountPLN"), min) — tworzy predykat JPA odpowiadający SQL-owi amountPLN >= :min.
                min != null ? ... : null — jeśli min jest null, zwracasz null (czyli pomijasz ten filtr). Specification.where(...).and(...) ignoruje null-owe specyfikacje.
        */

        return (root, query, cb) -> category ? cb.equal(root.get("category"), category) : null
    }

    static Specification<TransactionEntity> amountGreaterThan(BigDecimal min) {
        return (root, query, cb) -> min != null ? cb.greaterThanOrEqualTo(root.get("amountPLN"), min) : null
    }

    static Specification<TransactionEntity> descriptionLike(String text) {
        return (root, query, cb) -> text ? cb.like(cb.lower(root.get("description")), "%${text.toLowerCase()}%") : null
    }
}