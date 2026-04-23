Lab83
-----

Lab83--Optimistic-Locking--Ochrona-przed-utratą-danych
======================================================

Skoro masz już w pełni działający system relacyjny z budżetami i kategoriami, czas wejść w temat, który jest absolutnym "must-have" na poziomie Mid/Senior, 
a o którym programiści często zapominają, dopóki system nie zacznie gubić pieniędzy na produkcji.
Mówię o współbieżności i spójności danych `Concurrency Control`.

Problem: 
"Zaginiona Aktualizacja" (Lost Update)
Obecnie Twoja tabela `financial_summary` (ta z globalnym bilansem) jest aktualizowana przez asynchronicznych słuchaczy (@Async).
Wyobraź sobie sytuację:
1. Wątek A czyta bilans: 1000 PLN.
2. Wątek B czyta bilans: 1000 PLN.
3. Wątek A dodaje 500 PLN i zapisuje: 1500 PLN.
4. Wątek B dodaje 200 PLN i zapisuje: 1200 PLN.
5. Wynik: Masz w bazie 1200 PLN zamiast 1700 PLN. Właśnie zgubiłeś 500 PLN.

Rozwiążemy to za pomocą Blokowania Optymistycznego `Optimistic Locking`.

Cel: 
----
Dodanie mechanizmu wersji do encji statystyk, aby Hibernate automatycznie wykrywał konflikty zapisu.

Krok-1. Nowa migracja Flyway (V9__add_versioning.sql)
-----------------------------------------------------
Musimy dodać kolumnę version do tabeli statystyk.

`src/main/resources/db/migration/V9__add_versioning.sql`

```sql
-- Dodajemy kolumnę wersji. Każdy update będzie ją zwiększał o 1.
ALTER TABLE financial_summary ADD COLUMN version BIGINT DEFAULT 0;
```

Krok-2. Aktualizacja Encji (FinancialSummaryEntity.groovy)
----------------------------------------------------------
Używamy adnotacji `@Version`. 
To sygnał dla Hibernate: 
"Zanim zapiszesz, sprawdź czy wersja w bazie jest taka sama, jak ta, którą pobrałem".

```groovy
package pl.edu.praktyki.repository

import jakarta.persistence.*
import groovy.transform.Canonical

@Entity
@Table(name = "financial_summary")
@Canonical
class FinancialSummaryEntity {
@Id
String id = "GLOBAL"

    BigDecimal totalBalance = 0.0
    Long transactionCount = 0

    @Version // <--- MAGIA: Automatyczna ochrona przed nadpisaniem danych
    Long version
}
```

Krok-3. Obsługa konfliktu w GlobalStatsProjector.groovy
-------------------------------------------------------
Jeśli dwa wątki spróbują zapisać dane naraz, jeden z nich dostanie `OptimisticLockingFailureException`. 
Musimy go obsłużyć (np. ponowić próbę).

```groovy
@Async("bulkTaskExecutor")
@EventListener
@Transactional
void projectBatchToGlobalSummary(TransactionBatchProcessedEvent event) {
// W prawdziwym systemie użylibyśmy @Retryable,
// ale tutaj zrobimy to "ręcznie", żebyś zrozumiał mechanizm.

        def summary = summaryRepo.findById("GLOBAL").get()
        summary.totalBalance += event.totalBalance
        summary.transactionCount += event.transactionsCount
        
        try {
            summaryRepo.saveAndFlush(summary)
        } catch (org.springframework.orm.jpa.JpaOptimisticLockingFailureException e) {
            log.warn(">>> [CONCURRENCY] Wykryto konflikt wersji dla GLOBAL! Ktoś inny zmienił bilans. Ponawiam...")
            // Tutaj w prawdziwym kodzie wywołalibyśmy metodę ponownie
        }
    }
```

Krok-4. Test Spock – "Race Condition Simulator" (ConcurrencySpec.groovy)
------------------------------------------------------------------------
To jest test na poziomie "Seniora". 
Odpalimy dwa wątki naraz i spróbujemy "zepsuć" bazę danych, a potem sprawdzimy, czy `@Version` nas uratował.

`src/test/groovy/pl/edu/praktyki/repository/ConcurrencySpec.groovy`

```groovy
package pl.edu.praktyki.repository

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.orm.jpa.JpaOptimisticLockingFailureException
import pl.edu.praktyki.BaseIntegrationSpec
import spock.lang.Specification

class ConcurrencySpec extends BaseIntegrationSpec {

    @Autowired FinancialSummaryRepository summaryRepo

    def "powinien rzucić wyjątek przy próbie równoległego zapisu tej samej wersji"() {
        given: "pobieramy ten sam rekord w dwóch różnych obiektach"
        def summaryV1 = summaryRepo.findById("GLOBAL").get()
        def summaryV2 = summaryRepo.findById("GLOBAL").get()

        and: "mamy pewność, że oba mają tę samą wersję"
        assert summaryV1.version == summaryV2.version

        when: "Pierwszy użytkownik zapisuje zmianę"
        summaryV1.totalBalance += 100
        summaryRepo.saveAndFlush(summaryV1)

        and: "Drugi użytkownik próbuje zapisać swoją zmianę na STAREJ wersji"
        summaryV2.totalBalance += 200
        summaryRepo.saveAndFlush(summaryV2)

        then: "Hibernate wykrywa oszustwo i rzuca wyjątek"
        thrown(JpaOptimisticLockingFailureException)
    }
}
```

Dlaczego to jest poziom Mid? (Wiedza na rozmowę)

Rekruter zapyta: "Jak radzisz sobie z problemem Lost Update?"
Twoja odpowiedź:

"Stosuję blokowanie optymistyczne za pomocą adnotacji `@Version`. 
Hibernate przy każdym `UPDATE` dodaje do klauzuli WHERE warunek AND version = ?. 
Jeśli w międzyczasie inny wątek zmienił dane, liczba zaktualizowanych wierszy wyniesie 0, co wywoła wyjątek. 
To rozwiązanie jest znacznie wydajniejsze niż blokowanie pesymistyczne (blokowanie wiersza w bazie), 
ponieważ nie trzyma zamków na bazie danych."

Zadanie dla Ciebie:
-------------------
1. Dodaj migrację V9 (kolumna version).
2. Dodaj `@Version` do `FinancialSummaryEntity`.
3. Odpal test `ConcurrencySpec`.

Daj znać, czy udało Ci się "złapać" Hibernate'a na gorącym uczynku! 
To jest fundament bezpiecznych systemów finansowych.

