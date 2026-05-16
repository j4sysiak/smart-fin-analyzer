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


/*
    Pytanie:
    --------
    gdzie jest „biznesowe” wywołanie tych metod?

    Odpowiedź:
    ----------
    To nie jest zwykle wołane ręcznie.
    @PostPersist oznacza callback JPA/Hibernate - metoda uruchamia się automatycznie po zapisaniu encji TransactionEntity do bazy,
    gdy wykonuje się persist / save i encja zostanie faktycznie utrwalona.
    Żeby to zadziałało, listener musi być podpięty do encji, zwykle w TransactionEntity, np. przez @EntityListeners(...).
    Czyli przepływ jest taki:
     - kod aplikacji zapisuje TransactionEntity,
     - Hibernate wykonuje INSERT,
     - po tym wywołuje postPersist(...),
     - listener zapisuje audit przez TransactionAuditWriter.

    Jeśli chcesz znaleźć miejsce „biznesowego” wywołania, szukaj w projekcie zapisów TransactionEntity, np.:
    transactionRepository.save(...)
    entityManager.persist(...)
    serwisów operujących na TransactionEntity
    */

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

