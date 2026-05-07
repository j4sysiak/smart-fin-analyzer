package pl.edu.praktyki.repository

import jakarta.persistence.PostPersist
import jakarta.persistence.PostRemove
import jakarta.persistence.PostUpdate

/**
 * Manualny audit dla TransactionEntity.
 *
 * Dlaczego nie Envers?
 * Hibernate 6.6.13.Final nadal wywala ClassCastException podczas budowy SessionFactory
 * dla tej encji w Groovy. Kategoria i reszta projektu mogą dalej używać Enversa,
 * ale dla transactions zapisujemy wpisy do revinfo + transactions_aud ręcznie,
 * zachowując ten sam format tabel audytowych.
 */
class TransactionAuditEntityListener {

    @PostPersist
    void postPersist(TransactionEntity entity) {
        SpringContextHolder.getBean(TransactionAuditWriter).writeRevision(entity, 0)
    }

    @PostUpdate
    void postUpdate(TransactionEntity entity) {
        SpringContextHolder.getBean(TransactionAuditWriter).writeRevision(entity, 1)
    }

    @PostRemove
    void postRemove(TransactionEntity entity) {
        SpringContextHolder.getBean(TransactionAuditWriter).writeRevision(entity, 2)
    }
}

