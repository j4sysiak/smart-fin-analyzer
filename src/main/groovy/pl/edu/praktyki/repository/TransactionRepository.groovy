package pl.edu.praktyki.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.stereotype.Repository

@Repository
interface TransactionRepository extends JpaRepository<TransactionEntity, Long>, JpaSpecificationExecutor<TransactionEntity> {

    // dzięki temu, że dziedziczymy po JpaSpecificationExecutor,
    // możemy korzystać z dynamicznych zapytań bez konieczności definiowania metod findBy...
    // Nie musisz tu już pisać żadnych metod findBy... !

    // Spring Data sam wygeneruje zapytanie z LIMIT i OFFSET
    // Page<TransactionEntity> findByCategory(String category, Pageable pageable)
}

