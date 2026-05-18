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
import java.time.ZonedDateTime

@ActiveProfiles("tc")
@Import(TimeTravelMonthBoundarySpec.TimeMachineConfig)
class TimeTravelMonthBoundarySpec extends BaseIntegrationSpec {

    @Autowired BudgetService budgetService
    @Autowired CategoryRepository categoryRepository
    @Autowired TransactionRepository transactionRepository
    @Autowired MutableClock mutableClock

    @TestConfiguration
    static class TimeMachineConfig {

        @Bean
        @Primary
        MutableClock mutableClock() {
            new MutableClock(
                    Instant.parse("2026-03-31T12:00:00Z"),
                    ZoneId.of("UTC")
            )
        }

        @Bean
        @Primary
        Clock clock(MutableClock mutableClock) {
            // BudgetService wstrzyknie ten bean jako Clock
            mutableClock
        }
    }


    // Testowy, przestawialny zegar, żeby sprawdzić zachowanie BudgetService dla różnych dat.
    // Ta klasa służy do sterowania czasem w teście.
    // MutableClock rozszerza Clock, więc może zostać wstrzyknięty tam,
    // gdzie aplikacja oczekuje zwykłego Clock.
    // Różnica jest taka, że tutaj czas można ręcznie zmieniać przez setInstant(...).
    // W tym teście daje to kilka korzyści:
    //  - ustawia przewidywalny moment startowy - test nie zależy od prawdziwej daty systemowej,
    //  - pozwala zasymulować przejście do kolejnego miesiąca bez czekania,
    //  - umożliwia sprawdzenie logiki resetu limitu miesięcznego dokładnie na granicy miesięcy.
    //
    //    Rola metod:
    //    - instant() - zwraca aktualny „udawany” czas,
    //    - setInstant(...) - zmienia ten czas w trakcie testu,
    //    - getZone() - zwraca strefę czasową,
    //    - withZone(...) - tworzy zegar w innej strefie.
    static class MutableClock extends Clock {
        private Instant currentInstant
        private final ZoneId zone

        MutableClock(Instant initialInstant, ZoneId zone) {
            this.currentInstant = initialInstant
            this.zone = zone
        }

        void setInstant(Instant newInstant) {
            this.currentInstant = newInstant
        }

        @Override
        ZoneId getZone() {
            zone
        }

        @Override
        Clock withZone(ZoneId zone) {
            new MutableClock(this.currentInstant, zone)
        }

        @Override
        Instant instant() {
            currentInstant
        }
    }

    def "powinien resetować limit po przejściu do nowego miesiąca"() {
        given:
        def cat = categoryRepository.saveAndFlush(new CategoryEntity(
                name: "GranicaMiesiaca",
                monthlyLimit: 500.0G
        ))

        // Wydatek z marca (ma być liczony tylko w marcu)
        transactionRepository.saveAndFlush(new TransactionEntity(
                originalId: "MB-1",
                ownerUsername: "user_month_boundary",
                date: LocalDate.of(2026, 3, 15),
                amount: -450.0G,
                currency: "PLN",
                amountPLN: -450.0G,
                category: cat.name,
                categoryEntity: cat,
                description: "March expense"
        ))

        expect: "w marcu nowa transakcja 100 przekroczy limit (450 + 100 > 500)"
        budgetService.isOverBudget(cat, -100.0G) == true

        when: "podróżujemy w czasie do kwietnia"
        mutableClock.setInstant(ZonedDateTime.of(2026, 4, 1, 10, 0, 0, 0, ZoneId.of("UTC")).toInstant())

        then: "w kwietniu marcowe wydatki nie liczą sie do limitu"
        budgetService.isOverBudget(cat, -100.0G) == false
    }
}