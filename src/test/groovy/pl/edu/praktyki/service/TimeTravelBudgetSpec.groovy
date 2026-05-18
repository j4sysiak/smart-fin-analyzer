package pl.edu.praktyki.service

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.test.context.ActiveProfiles
import pl.edu.praktyki.BaseIntegrationSpec
import pl.edu.praktyki.repository.CategoryEntity
import pl.edu.praktyki.repository.CategoryRepository
import pl.edu.praktyki.repository.TransactionEntity
import pl.edu.praktyki.repository.TransactionRepository

import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@ActiveProfiles("tc")
@Import(TimeTravelBudgetSpec.TimeMachineConfig)
class TimeTravelBudgetSpec extends BaseIntegrationSpec {

    @Autowired BudgetService budgetService
    @Autowired CategoryRepository categoryRepository
    @Autowired TransactionRepository transactionRepository

    @TestConfiguration
    static class TimeMachineConfig {
        @Bean
        @Primary
        Clock fixedClock() {
            // 15 marca 2026, stały punkt w czasie
            return Clock.fixed(Instant.parse("2026-03-15T10:00:00Z"), ZoneId.of("UTC"))
        }
    }

    def "powinien liczyć limit tylko dla transakcji z marca 2026"() {
        given:
        def cat = categoryRepository.saveAndFlush(new CategoryEntity(
                name: "Podroze",
                monthlyLimit: 500.0G
        ))

        // Rekord z marca (powinien wejść do sumy)
        transactionRepository.saveAndFlush(new TransactionEntity(
                originalId: "TT-1",
                ownerUsername: "user_time",
                date: LocalDate.of(2026, 3, 10),
                amount: -300.0G,
                currency: "PLN",
                amountPLN: -300.0G,
                category: cat.name,
                categoryEntity: cat,
                description: "March expense"
        ))

        // Rekord z lutego (nie powinien wejść do sumy marca)
        transactionRepository.saveAndFlush(new TransactionEntity(
                originalId: "TT-2",
                ownerUsername: "user_time",
                date: LocalDate.of(2026, 2, 20),
                amount: -400.0G,
                currency: "PLN",
                amountPLN: -400.0G,
                category: cat.name,
                categoryEntity: cat,
                description: "February expense"
        ))

        expect:
        // 300 (marzec) + 100 (nowa transakcja) = 400 <= 500
        budgetService.isOverBudget(cat, -100.0G) == false

        and:
        // 300 (marzec) + 250 (nowa) = 550 > 500
        budgetService.isOverBudget(cat, -250.0G) == true
    }
}