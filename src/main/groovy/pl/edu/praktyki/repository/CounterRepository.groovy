package pl.edu.praktyki.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface CounterRepository extends JpaRepository<Counter, Long> {

/*
    Baza danych radzi sobie z wielowątkową inkrementacją dzięki kilku mechanizmom:
    1. Atomowa operacja SQL
          w TransactionCounterService.increment() używasz zapytania typu:
    UPDATE counter SET value = value + 1 WHERE name = 'requests'
    Taka operacja jest atomowa na poziomie bazy danych — silnik DB (np. PostgreSQL, H2)
    sam blokuje wiersz na czas aktualizacji (row-level locking).
 */

    @Modifying
    @Query("UPDATE Counter c SET c.value = c.value + 1 WHERE c.name = :name")
    void incrementByName(@Param("name") String name)

    java.util.Optional<Counter> findByName(String name)
}
