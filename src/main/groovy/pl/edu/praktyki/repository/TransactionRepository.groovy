package pl.edu.praktyki.repository

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.util.stream.Stream

@Repository
interface TransactionRepository extends JpaRepository<TransactionEntity, Long>, JpaSpecificationExecutor<TransactionEntity> {

    @Override
    @EntityGraph(attributePaths = ["categoryEntity"])
    List<TransactionEntity> findAll()

    // dzięki temu, że dziedziczymy po JpaSpecificationExecutor,
    // możemy korzystać z dynamicznych zapytań bez konieczności definiowania metod findBy...
    // Nie musisz tu już pisać żadnych metod findBy... !

    // Spring Data sam wygeneruje zapytanie z LIMIT i OFFSET
    // Page<TransactionEntity> findByCategory(String category, Pageable pageable)

    // Znajdź transakcje po originalId - przydatne w testach oraz przy deduplikacji
    List<TransactionEntity> findByOriginalId(String originalId)

    // TA LINIA JEST KLUCZOWA:
    // Spring Data musi wiedzieć, że szukamy po polu categoryEntity (relacja ManyToOne)
    List<TransactionEntity> findByCategoryEntityAndDateBetween(CategoryEntity category, java.time.LocalDate start, java.time.LocalDate end)

    // Pobieranie wszystkich transakcji przypisanych do danej kategorii.
    List<TransactionEntity> findAllByCategoryEntity(CategoryEntity categoryEntity)

    // Szybkie sprawdzenie, czy kategoria jest używana przez jakiekolwiek transakcje.
    long countByCategoryEntity(CategoryEntity categoryEntity)

    // Jeśli używasz też wyszukiwania z paginacją, to również:
    Page<TransactionEntity> findByCategoryEntity(CategoryEntity category, Pageable pageable)


    // Ta metoda jest kluczowa dla izolacji danych
    // Lab89--Izolacja-Danych--Spring-Data-Repository-Methods
    Page<TransactionEntity> findAllByOwnerUsername(String ownerUsername, Pageable pageable)


    Optional<TransactionEntity> findByDbIdAndOwnerUsername(Long dbId, String ownerUsername)

    //Lab91--Streaming Data Export -- Eksport CSV bez obciążania RAM-u
    @Query("SELECT t FROM TransactionEntity t WHERE t.ownerUsername = :username")
    Stream<TransactionEntity> streamAllByOwnerUsername(@Param("username") String username)

}