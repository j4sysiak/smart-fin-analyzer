package pl.edu.praktyki.repository

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface TransactionRepository extends JpaRepository<TransactionEntity, Long> {

    // Spring Data sam wygeneruje zapytanie z LIMIT i OFFSET
    Page<TransactionEntity> findByCategory(String category, Pageable pageable)
}