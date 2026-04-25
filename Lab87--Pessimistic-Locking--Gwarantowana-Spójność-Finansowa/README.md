Lab87
-----

Lab87--Pessimistic-Locking--Gwarantowana-Spójność-Finansowa
===========================================================

Problem:
izolacja transakcji i blokowanie pesymistyczne (Pessimistic Locking).

Dlaczego ten Lab? (Problem biznesowy)

W Labie 83 zrobiliśmy Optimistic Locking (@Version). 
To działa super, gdy konflikty zdarzają się rzadko. 
Ale wyobraź sobie, że Twój system finansowy obsługuje „Wspólny Portfel” (np. konto firmowe), do którego 100 osób naraz wrzuca wydatki.

Przy blokowaniu optymistycznym 99 osób dostanie błąd "Conflict" i ich praca zostanie odrzucona. Frustrujące.

Przy blokowaniu pesymistycznym baza danych ustawi „kolejkę” przed tym jednym wierszem. 
Każdy poczeka ułamek sekundy, ale nikt nie zostanie odrzucony.

Cel: 
Zmiana mechanizmu aktualizacji globalnego bilansu na taki, który fizycznie blokuje wiersz w bazie danych na czas obliczeń.

Krok1. Rozbudowa Repozytorium, interface (FinancialSummaryRepository.groovy)
-----------------------------------------------------------------
Musimy dodać metodę, która mówi Postgresowi: 
"Zablokuj ten wiersz dla mnie (`SELECT FOR UPDATE`), dopóki nie skończę transakcji".

```groovy
package pl.edu.praktyki.repository

import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface FinancialSummaryRepository extends JpaRepository<FinancialSummaryEntity, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE) // <-- KLUCZ: Postgres blokuje wiersz dla innych
    @Query("SELECT s FROM FinancialSummaryEntity s WHERE s.id = :id")
    Optional<FinancialSummaryEntity> findByIdWithLock(@Param("id") String id)
}
```

Krok-2. Serwis Projekcji (GlobalStatsProjector.groovy)
------------------------------------------------------
Zmieniamy logikę na taką, która nie boi się wyścigów (Race Conditions), bo baza danych pilnuje kolejki.

```groovy
@Async("smartFinExecutor")
@EventListener
@Transactional // Bardzo ważne: blokada żyje tylko wewnątrz transakcji!
void projectBatchToGlobalSummary(TransactionBatchProcessedEvent event) {
log.info(">>> [CQRS] Próba blokady wiersza GLOBAL dla: {}", event.userName)

        // 1. Pobieramy z blokadą PESYMISTYCZNĄ. 
        // Jeśli inny wątek już to trzyma, ten wątek tu ZAWISNIE i poczeka grzecznie.
        def summary = summaryRepo.findByIdWithLock("GLOBAL").orElseGet {
            new FinancialSummaryEntity(id: "GLOBAL", totalBalance: 0.0, transactionCount: 0)
        }

        // 2. Modyfikujemy dane (mamy gwarancję, że nikt inny teraz tego nie robi)
        summary.totalBalance += event.totalBalance
        summary.transactionCount += (event.transactionsCount ?: 0)

        // 3. Zapisujemy
        summaryRepo.save(summary)
        
        log.info(">>> [CQRS] Blokada zwolniona. Nowy bilans: {}", summary.totalBalance)
        // Po wyjściu z metody transakcja się kończy (commit), a Postgres puszcza kolejną osobę do wiersza.
    }
```

Krok-3. Test Spock – "Heavy Contention Stress Test" (PessimisticLockingSpec.groovy)
-----------------------------------------------------------------------------------
Napiszemy test, który odpali 50 wątków naraz, z których każdy będzie próbował zaktualizować ten sam wiersz w tym samym czasie. 
Przy blokowaniu optymistycznym test by padł. 
Tutaj musi przejść.

src/test/groovy/pl/edu/praktyki/repository/PessimisticLockingSpec.groovy:

```groovy
package pl.edu.praktyki.repository

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationEventPublisher
import pl.edu.praktyki.BaseIntegrationSpec
import pl.edu.praktyki.event.TransactionBatchProcessedEvent
import java.util.concurrent.TimeUnit
import static org.awaitility.Awaitility.await

class PessimisticLockingSpec extends BaseIntegrationSpec {

    @Autowired ApplicationEventPublisher eventPublisher
    @Autowired FinancialSummaryRepository summaryRepo

    def "powinien bezbłędnie zsumować 50 równoległych paczek danych (Stress Test Blokady)"() {
        given: "czyścimy stan początkowy"
        def summary = summaryRepo.findById("GLOBAL").get()
        def startBalance = summary.totalBalance
        int numberOfBatches = 50
        BigDecimal amountPerBatch = 10.0

        when: "bombardujemy system 50 zdarzeniami naraz"
        (1..numberOfBatches).each {
            eventPublisher.publishEvent(new TransactionBatchProcessedEvent(
                userName: "StressUser", 
                totalBalance: amountPerBatch, 
                transactionsCount: 1
            ))
        }

        then: "Czekamy, aż wszystkie 50 asynchronicznych wątków przepchnie się przez blokadę bazy"
        await().atMost(20, TimeUnit.SECONDS).until {
            summaryRepo.findById("GLOBAL").get().totalBalance == startBalance + (numberOfBatches * amountPerBatch)
        }
        
        expect: "Suma jest idealna, co do grosza"
        summaryRepo.findById("GLOBAL").get().totalBalance == startBalance + 500.0
    }
}
```

Dlaczego to jest poziom Mid/Senior?

Concurrency Control: 
Rozumiesz różnicę między OPTIMISTIC_LOCKING używającym `@Version` (fail-fast)  a  `PESSIMISTIC_WRITE` używający (wait-for-lock).

Transaction Boundaries: Wiesz, że blokada DB działa tylko wewnątrz @Transactional.

Performance vs Integrity: Potrafisz ocenić, kiedy warto spowolnić system (kolejka do wiersza), żeby uratować spójność danych finansowych.

Zadanie:

Dodaj metodę findByIdWithLock do repozytorium.

Zaktualizuj GlobalStatsProjector.

Odpal PessimisticLockingSpec.

Wskazówka: Jeśli test będzie trwał długo, to dobrze! Zobaczysz w logach, jak wątki po kolei meldują: Próba blokady... -> Blokada zwolniona. To jest Twój system "kolejkujący" dostęp do pieniędzy.

Daj znać, jak poszło z tym "przeciążeniem" Postgresa! 


/////////////////

To, co widzisz, to niesamowicie pouczająca lekcja z zakresu zarządzania zasobami (Thread Pool Contention). Twój test nie "zepsuł się" – on po prostu ujawnił wąskie gardło w architekturze, którą zbudowaliśmy.
Dlaczego test wywalił Timeout (310 PLN zamiast 500 PLN)?

Przeanalizujmy fakty z Twoich logów:
W `AsyncNotificationService` masz ustawione sleep(6000) (6 sekund).

Wysłałeś 50 paczek (eventów).

Każdy event budzi 3 słuchaczy (Audit, Notification, Projector). 
Wszystkie korzystają z tej samej puli `bulkTaskExecutor`.
Pula ma `maxPoolSize = 16`.

Matematyka jest bezlitosna:
Aby obsłużyć 50 powiadomień, z których każde śpi 6 sekund, Twoje wątki muszą łącznie "przestać"
50 × 6𝑠 = 300s
 
Przy 16 wątkach zajmie to co najmniej
300 / 16 ≈ 18.75s
 
Logi pokazują, że o godzinie 18:53:34 (czyli po ok. 19 sekundach) system dobił do 310 PLN (31 paczek). 
Test skończył się o sekundę później (limit 20s), więc zabrakło mu czasu na przetworzenie pozostałych 19 paczek.

Rozwiązanie (Podejście Mid-level)
Jako Mid-developer masz 2 drogi, aby to naprawić:

Opcja A: Zwiększ cierpliwość testu (Najszybsze)
-----------------------------------------------
Zwiększamy timeout w await(), bo wiemy, że nasz system ma "ciężkie" zadania w tle (ten 6-sekundowy sleep).

```groovy
// W PessimisticLockingSpec.groovy
await().atMost(60, TimeUnit.SECONDS).until { // Zwiększamy do 60s
summaryRepo.findById("GLOBAL").get().totalBalance == startBalance + 500.0
}
```

Opcja B: Separacja Pul Wątków (Najbardziej profesjonalne) - opcja Bulkheading
-----------------------------------------------------------------------------
To jest to, co odróżnia Seniora. 
Nigdy nie używamy tej samej puli wątków dla zadań krytycznych (Baza/Finanse) i zadań powolnych (E-maile/Powiadomienia).

Stwórz w `AsyncConfig` drugą pulę `notificationExecutor` z dużym `queueCapacity`.
W `AsyncNotificationService` zmień adnotację na `@Async("notificationExecutor")`.
Dzięki temu "śpiące" maile nie będą blokować "pracowitego" Projectora statystyk.



Zastosujmy Opcję A (zwiększ czas), żeby test przeszedł, ale pamiętaj o wniosku z Opcji B, bo to jest świetna anegdota na rozmowę rekrutacyjną!

Rekruter: "Co zrobisz, gdy jeden powolny proces asynchroniczny blokuje inne?"
Ty: "Zastosuję Bulkheading, czyli odseparuję pule wątków dla różnych typów zadań, 
aby awaria lub spowolnienie modułu powiadomień nie wpłynęło na spójność statystyk finansowych."

 






