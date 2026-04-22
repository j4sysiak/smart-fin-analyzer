package pl.edu.praktyki.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

/*
 Optional:
 Użycie Optional to standard w nowoczesnej Javie/Groovym.
 Zmusza Cię to do obsłużenia przypadku, gdy ktoś poda w CSV kategorię,
 której nie masz jeszcze zdefiniowanej w bazie (unikasz NullPointerException).
*/
import java.util.Optional

@Repository
interface CategoryRepository extends JpaRepository<CategoryEntity, Long> {

    /**
     * Kluczowa metoda dla naszego systemu.
     * Pozwala znaleźć kategorię po nazwie (np. 'Jedzenie')
     * zamiast posługiwać się technicznym ID.
     */

    // Spring Data Magic: Zauważ, że zdefiniowaliśmy tylko nagłówek metody.
    // Spring sam wygeneruje zapytanie SQL: SELECT * FROM categories WHERE name = ?.

    Optional<CategoryEntity> findByName(String name)
}