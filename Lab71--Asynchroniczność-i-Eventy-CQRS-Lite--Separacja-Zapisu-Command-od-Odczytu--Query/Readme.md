Lab71
-----

Lab71--Asynchroniczność-i-Eventy-CQRS-Lite--Separacja-Zapisu-Command-od-Odczytu--Query
--------------------------------------------------------------------------------------

Teraz wchodzimy w najgłębszą część Architektury Aplikacji. 
To jest temat, który na rozmowach kwalifikacyjnych na Mida/Seniora oddziela "kodujących user stories" od tych, 
którzy rozumieją "przepływ danych".

`CQRS (Command Query Responsibility Segregation)` brzmi strasznie, ale "na wesoło" oznacza: 
Oddzielenie "Robienia rzeczy" od "Oglądania rzeczy".

Problem: 
Obecnie Twoja baza danych PostgreSQL robi wszystko. 
Kiedy pobierasz statystyki `/stats`, Spring musi przeliczyć tysiące rekordów w locie. 
To wolne.

Rozwiązanie `CQRS`:
-------------------
Command: 
Ktoś dodaje transakcję. Zapisujemy ją do głównej tabeli.

Event: 
System rzuca zdarzenie: "Hej, mamy nową kasę!".
Publikujemy `TransactionBatchProcessedEvent` z informacją o nowym bilansie.

Projection (Query) - `SummaryProjectionListener`: 
Specjalny "Słuchacz" łapie to zdarzenie i aktualizuje osobną, malutką tabelkę ze statystykami `financial_summary`.
Dzięki temu, gdy użytkownik pyta później o bilans, nie liczymy niczego – po prostu czytamy jeden gotowy wynik z tabeli statystyk. 
Szybkość: 0.001ms.

Musimy jednak najpierw mieć tak malutk tabelę "widoku" (projekcji), która trzyma gotowy wynik "na już" - `financial_summary`.
Model (Encja) i Repozytorium dla tej tabeli są proste, ale kluczowe.


Krok 1: Nowa Encja Statystyk (FinancialSummaryEntity.groovy)
------------------------------------------------------------
Encja dla tabeli "widoku" (projekcji):
`src/main/groovy/pl/edu/praktyki/repository/FinancialSummaryEntity.groovy`

```groovy
package pl.edu.praktyki.repository

import jakarta.persistence.*

@Entity
@Table(name = "financial_summary")
class FinancialSummaryEntity {
@Id
String id = "GLOBAL" // Mamy tylko jeden wiersz z globalnym bilansem

    BigDecimal totalBalance = 0.0
    Long transactionCount = 0
}
```

Migracja do stworzenia tej tabeli i wstawienia startowego wiersza:
 
`src/main/resources/db/migration/V4__summary_table.sql`
```sql
CREATE TABLE financial_summary (
id VARCHAR(255) PRIMARY KEY,
total_balance DECIMAL(19, 2) DEFAULT 0.0,
transaction_count BIGINT DEFAULT 0
);

INSERT INTO financial_summary (id, total_balance, transaction_count) VALUES ('GLOBAL', 0.0, 0);
```


Krok 2: Repozytorium dla tej encji (FinancialSummaryRepository.groovy)
----------------------------------------------------------------------
Dodaj odpowiednie repozytorium (`FinancialSummaryRepository`) dla nowej encji (`FinancialSummaryEntity`) - to po prostu interfejs `JpaRepository`.
Repozytorium dla tej encji:
`src/main/groovy/pl/edu/praktyki/repository/FinancialSummaryRepository.groovy`

```groovy
package pl.edu.praktyki.repository
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface FinancialSummaryRepository extends JpaRepository<FinancialSummaryEntity, String> {}
```


Krok 3: Słuchacz Projekcji (SummaryProjectionListener.groovy)
-------------------------------------------------------------
To jest nasz "robot", który pracuje w tle.
To jest serce "CQRS na wesoło". 
Ten gość słucha Twoich eventów i aktualizuje widok (projekcję) w bazie - aktualizuje malutka tabelę: `financial_summary`.
Zwróć uwagę na `@Async` – dzięki temu aktualizacja statystyk nie spowalnia głównego procesu importu.

`src/main/groovy/pl/edu/praktyki/service/SummaryProjectionListener.groovy`

```groovy
package pl.edu.praktyki.service

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import pl.edu.praktyki.event.TransactionBatchProcessedEvent
import pl.edu.praktyki.repository.FinancialSummaryEntity
import pl.edu.praktyki.repository.FinancialSummaryRepository
import groovy.util.logging.Slf4j

@Service
@Slf4j
class SummaryProjectionListener {

    @Autowired FinancialSummaryRepository summaryRepo

    @EventListener
    @Transactional // Bardzo ważne: aktualizacja statystyk musi być atomowa!
    void handleNewTransactions(TransactionBatchProcessedEvent event) {
        log.info(">>> [CQRS] Aktualizuję projekcję statystyk dla paczki od: {}", event.userName)

        // BEZPIECZNE POBIERANIE: Jeśli nie ma wiersza GLOBAL, stwórz go w pamięci
        def summary = summaryRepo.findById("GLOBAL").orElseGet {
            log.info(">>> [CQRS] Wiersz GLOBAL nie istniał, tworzę go...")
            return new FinancialSummaryEntity(id: "GLOBAL", totalBalance: 0.0, transactionCount: 0)
        }

        // Aktualizujemy gotowy widok
        summary.totalBalance += event.totalBalance
        summary.transactionCount += 1 // Liczymy paczki lub transakcje

        summaryRepo.save(summary)
        log.info(">>> [CQRS] Nowy bilans globalny: {}", summary.totalBalance)
    }
}
```


Krok 4: Udostępnienie statystyk w Kontrolerze (TransactionController.groovy)
----------------------------------------------------------------------------
Teraz odczyt statystyk nie woła analyticsService.calculate... (który przelicza bazę), 
ale bierze gotowy wynik z repozytorium statystyk.

Dodaj do `TransactionController.groovy`:

```groovy
@Autowired FinancialSummaryRepository summaryRepo

    @GetMapping("/total-summary")
    Map<String, Object> getGlobalSummary() {
        def summary = summaryRepo.findById("GLOBAL").orElse(new FinancialSummaryEntity())
        return [
            globalTotalBalance: summary.totalBalance,
            syncTimestamp: java.time.LocalDateTime.now()
        ]
    }
```


Krok 5: Test Spock – Weryfikacja Rzeczywista (CqrsSpec.groovy)
--------------------------------------------------------------
Ten test sprawdzi, czy po imporcie danych, statystyka w osobnej tabeli "sama" się zmieniła.

`src/test/groovy/pl/edu/praktyki/event/CqrsSpec.groovy`

```groovy
package pl.edu.praktyki.event

import pl.edu.praktyki.BaseIntegrationSpec
import org.springframework.beans.factory.annotation.Autowired
import pl.edu.praktyki.facade.SmartFinFacade
import pl.edu.praktyki.domain.Transaction
import pl.edu.praktyki.repository.FinancialSummaryRepository
import static org.awaitility.Awaitility.await
import java.util.concurrent.TimeUnit
import java.time.LocalDate

class CqrsSpec extends BaseIntegrationSpec {

    @Autowired SmartFinFacade facade
    @Autowired FinancialSummaryRepository summaryRepo

    def "powinien asynchronicznie zaktualizować tabelę statystyk po zakończeniu importu"() {
        given: "obecny stan statystyk w bazie"
        // Jeśli baza jest czysta, total będzie 0
        def initialBalance = summaryRepo.findById("GLOBAL")
                .orElse(new pl.edu.praktyki.repository.FinancialSummaryEntity())
                .totalBalance

        and: "nowa paczka danych na 1000 PLN"
        def data = [new Transaction(id: "CQRS-TEST", amount: 1000, currency: "PLN", category: "Test", date: LocalDate.now())]

        when: "wykonujemy proces przez Fasadę"
        facade.processAndGenerateReport("CqrsTester", data, [])

        then: "Czekamy, aż asynchroniczny listener (SummaryProjectionListener) zaktualizuje tabelę statystyk"
        await().atMost(10, TimeUnit.SECONDS).until {
            def current = summaryRepo.findById("GLOBAL").get()
            return current.totalBalance == initialBalance + 1000.0
        }
        
        and: "Finalna wartość jest poprawna"
        summaryRepo.findById("GLOBAL").get().totalBalance == initialBalance + 1000.0
    }
}
```

Dlaczego to jest "Mastery" w oczach rekrutera?
----------------------------------------------
Eventual Consistency: 
Przyznajesz, że statystyki globalne nie muszą być odświeżone w tej samej milisekundzie, co zapis transakcji. 
Dzięki temu aplikacja jest niesamowicie szybka dla użytkownika.

Scalability: 
Twoje `/api/transactions/total-summary` zawsze odpowie w 1ms, niezależnie od tego, czy w bazie jest 10 rekordów, czy 10 miliardów.

Clean Separation: 
Masz oddzielny model dla danych historycznych (TransactionEntity) i oddzielny dla widoków (FinancialSummaryEntity).

Zadanie:
--------
Uruchom wg instrukcji: `C:\dev\smart-fin-analyzer\tmp\Readme--Ansible-Airflow.md`  
(## 2. Testy — PostgreSQL w Dockerze (Docker CLI))