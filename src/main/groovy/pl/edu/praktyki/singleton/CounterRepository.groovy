package pl.edu.praktyki.singleton

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface CounterRepository extends JpaRepository<Counter, Long> {

    @Modifying
    @Query("UPDATE Counter c SET c.value = c.value + 1 WHERE c.name = :name")
    void incrementByName(@Param("name") String name);

    java.util.Optional<Counter> findByName(String name);
}
