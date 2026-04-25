package pl.edu.praktyki.repository

import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface FinancialSummaryRepository extends JpaRepository<FinancialSummaryEntity, String> {

    // Lab 87: Dodajemy metodę z blokadą pesymistyczną.
    // Ta metoda będzie próbowała pobrać wiersz z blokadą, co oznacza, że jeśli inny wątek już trzyma tę blokadę,
    // ten wątek będzie czekał, aż się zwolni.
    // To jest właśnie magia blokady pesymistycznej!
    @Lock(LockModeType.PESSIMISTIC_WRITE) // <-- KLUCZ: Postgres blokuje wiersz dla innych
    @Query("SELECT s FROM FinancialSummaryEntity s WHERE s.id = :id")
    Optional<FinancialSummaryEntity> findByIdWithLock(@Param("id") String id)
}