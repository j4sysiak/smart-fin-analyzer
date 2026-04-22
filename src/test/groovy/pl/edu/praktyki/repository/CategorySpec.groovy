package pl.edu.praktyki.repository

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.annotation.Transactional
import pl.edu.praktyki.BaseIntegrationSpec
import pl.edu.praktyki.service.BudgetService
import java.time.LocalDate

class CategorySpec extends BaseIntegrationSpec {

    @Autowired TransactionRepository transactionRepository
    @Autowired CategoryRepository categoryRepository
    @Autowired BudgetService budgetService

    def setup() {
        // Najpierw usuwamy transakcje (zależne), potem kategorie (nadrzędne)
        // Używamy deleteAllInBatch() - jest szybsze i od razu wysyła SQL do bazy
        transactionRepository.deleteAllInBatch()
        categoryRepository.deleteAllInBatch()

        // Opcjonalnie: upewnij się, że Hibernate wyczyścił swój cache
        // Jeśli masz wstrzyknięty EntityManager em: em.clear()
    }

    def "should save category"() {
        given:
        // use the entity class expected by the repository
        def category = new CategoryEntity(name: "Test", monthlyLimit: 500.0)

        when:
        def savedCategory = categoryRepository.save(category)

        then:
        savedCategory.id != null
        savedCategory.name == "Test"
    }

    @Transactional // Transactional jest wymagany, aby leniwe ładowanie (LAZY) zadziałało w teście i dociagnąć powiązaną kategorię
    def "powinien poprawnie zapisać transakcję z powiązaną kategorią"() {
        given: "nowa kategoria z limitem"
        def foodCategory = new CategoryEntity(name: "Jedzenie", monthlyLimit: 1000.0)
        categoryRepository.save(foodCategory)

        and: "nowa transakcja przypisana do tej kategorii"
        def tx = new TransactionEntity(
                originalId: "REL-1",
                amount: 150.0,
                amountPLN: 150.0,
                category: foodCategory, // Przekazujemy obiekt, a nie String!
                date: LocalDate.now()
        )

        when: "zapisujemy transakcję"
        transactionRepository.save(tx)

        then: "możemy odczytać nazwę kategorii bezpośrednio z obiektu transakcji"
        def savedTx = transactionRepository.findAll()[0]
        savedTx.category.name == "Jedzenie"
        savedTx.category.monthlyLimit == 1000.0
    }

    @Transactional // Transactional jest wymagany, aby leniwe ładowanie (LAZY) zadziałało w teście
    def "powinien wykryć przekroczenie budżetu w kategorii"() {
        given: "kategoria z małym limitem"
        def funCategory = new CategoryEntity(name: "Rozrywka", monthlyLimit: 200.0)
        categoryRepository.save(funCategory)

        and: "istniejący wydatek w tej kategorii (150 PLN)"
        transactionRepository.save(new TransactionEntity(
                originalId: "OLD-1",
                amountPLN: -150.0,
                category: funCategory,
                date: LocalDate.now()
        ))

        expect: "nowy wydatek na 100 PLN przekroczy budżet (150 + 100 > 200)"
        budgetService.isOverBudget(funCategory, -100.0) == true

        and: "mały wydatek na 10 PLN nie przekroczy budżetu"
        budgetService.isOverBudget(funCategory, -10.0) == false
    }



    // Problem N+1 występuje, ponieważ transactionRepository.findAll() wykonuje 1 zapytanie żeby pobrać wszystkie transakcje,
    // a pole category jest ładowane LAZY.
    // Przy iteracji każde tx.category inicjalizuje proxy i powoduje dodatkowe zapytanie do bazy
    //   — 1 (pierwotne) + N (dla każdej transakcji).
    // Jak to widać w praktyce:
    //     zapytanie 1: SELECT * FROM transaction ...
    //     zapytanie 2..N+1: SELECT * FROM category WHERE id = ?

    // Szybkie sposoby naprawy problemu N+1:
    // użyć join fetch w zapytaniu:
    //   @Query("select t from TransactionEntity t join fetch t.category")
    //   List<TransactionEntity> findAllWithCategory();

    //  albo użyć @EntityGraph:

    // @EntityGraph(attributePaths = {"category"})
    // List<TransactionEntity> findAll();

    // albo

    // projektować DTO z joinem (unikasz ładowania encji) lub skonfigurować batch fetching.

    @Transactional // Transactional jest wymagany, aby leniwe ładowanie (LAZY) zadziałało w teście
    def "demonstracja problemu N+1 (logika Mida)"() {
        given: "kilka transakcji w różnych kategoriach"
        def cat1 = categoryRepository.save(new CategoryEntity(name: "C1", monthlyLimit: 100))
        def cat2 = categoryRepository.save(new CategoryEntity(name: "C2", monthlyLimit: 100))

        transactionRepository.save(new TransactionEntity(originalId: "T1", category: cat1, amountPLN: 10, date: LocalDate.now()))
        transactionRepository.save(new TransactionEntity(originalId: "T2", category: cat2, amountPLN: 20, date: LocalDate.now()))

        when: "pobieramy wszystkie transakcje"
        def allTransactions = transactionRepository.findAll()

        then: "wyciągnięcie nazwy kategorii `tx.category.name` dla każdej transakcji może spowodować dodatkowe zapytania"
        allTransactions.each { tx ->
            println "Transakcja ${tx.originalId} należy do kategorii: ${tx.category.name}"
        }
        allTransactions.size() == 2
    }
}
