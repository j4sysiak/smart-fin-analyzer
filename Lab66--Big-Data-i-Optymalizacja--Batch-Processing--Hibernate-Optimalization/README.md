Lab66
-----

Lab66--Big-Data-i-Optymalizacja--Batch-Processing--Hibernate-Optimalization
---------------------------------------------------------------------------

Teraz czas na Etap 3: 
Big Data i Optymalizacja Hibernate. 
To tutaj często "padają" aplikacje w firmach, gdy po kilku miesiącach działania baza danych zaczyna 
"puchnąć", a zapytania SQL trwają sekundy zamiast milisekund.

Problem: 
Jeśli w `IngesterService` zapisujesz transakcje w pętli `repo.save(tx)`, 
to dla 1000 transakcji wysyłasz do bazy 1000 osobnych zapytań INSERT. 
To jest zabójstwo dla wydajności sieci i bazy danych.

Cel: 
Zastosować `Batch Processing w Hibernate, aby wysyłać zapytania paczkami po np. 50 sztuk.

Krok 1: Optymalizacja application.properties
--------------------------------------------
Musimy "nakazać" Hibernate'owi, żeby grupował zapytania. 
Dodaj to do `src/main/resources/application.properties`:

```text
# Optymalizacja Hibernate dla dużej ilości danych
spring.jpa.properties.hibernate.jdbc.batch_size=50
spring.jpa.properties.hibernate.order_inserts=true
spring.jpa.properties.hibernate.order_updates=true
```


Krok 2: Kodowanie TransactionIngesterService
--------------------------------------------
W świecie Springa, zapisywanie tysięcy encji naraz najlepiej robić wewnątrz `@Transactional`.
Dodaj metodę w `pl.edu.praktyki.service.TransactionIngesterService.groovy`:

```groovy
    /**
 * Metoda Batch: Zamienia domenowe obiekty na encje i zapisuje je w jednej transakcji.
 */
@Transactional // <--- To gwarantuje, że wszystko pójdzie w jednej transakcji
List<Transaction> saveTransactionsBatch(List<Transaction> transactions) {
    // 1. Mapowanie z Domeny na Encje
    def entities = transactions.collect { tx ->
        new TransactionEntity(
                originalId: tx.id,
                date: tx.date,
                amount: tx.amount,
                currency: tx.currency,
                amountPLN: tx.amountPLN,
                category: tx.category,
                description: tx.description,
                tags: tx.tags
        )
    }
    // Dzięki batch_size=50 (w properitasach), repo.saveAll wyśle 50 INSERTÓW na raz!
    // 2. Zapis w trybie batchowym (zależnym od batch_size w properties)
    repo.saveAll(entities)

    return transactions
}
```

Krok 3: Wyzwanie – Symulacja "Big Data" (Test Obciążeniowy)
-----------------------------------------------------------
Napiszemy test, który wczyta 5000 transakcji i sprawdzi, czy czas zapisu jest akceptowalny.

Stwórz `src/test/groovy/pl/edu/praktyki/service/BatchPerformanceSpec.groovy`:

```groovy
package pl.edu.praktyki.service

import pl.edu.praktyki.BaseIntegrationSpec
import pl.edu.praktyki.domain.Transaction
import org.springframework.beans.factory.annotation.Autowired
import java.time.LocalDate

class BatchPerformanceSpec extends BaseIntegrationSpec {

    @Autowired TransactionIngesterService ingesterService

    def "powinien zapisać 5000 rekordów w czasie poniżej 2 sekund"() {
        given: "duża lista transakcji"
        def bulkData = (1..5000).collect { i ->
            new Transaction(id: "ID-$i", amount: 10.0, category: "Test", date: LocalDate.now())
        }

        when: "zapisujemy paczkę"
        long start = System.currentTimeMillis()
        ingesterService.saveTransactionsBatch(bulkData)
        long end = System.currentTimeMillis()

        then: "zapis był szybki"
        println ">>> [PERF] Zapis 5000 rekordów zajął: ${end - start}ms"
        (end - start) < 2000
    }
}
```
def "powinien szybko zapisać 5000 transakcji (Batch Processing)"() {
given: "5000 transakcji"
def bigData = (1..5000).collect {
new TransactionEntity(originalId: "ID-$it", amount: 10.0, category: "Test")
}

        when: "zapisujemy wszystko na raz"
        long start = System.currentTimeMillis()
        repository.saveAll(bigData)
        long end = System.currentTimeMillis()

        then: "zapis trwa mniej niż 2 sekundy (z batchingiem powinno być błyskawicznie)"
        (end - start) < 2000 
        repository.count() == 5000
    }
Pytania do Ciebie, żebyśmy dobrze weszli w ten temat:

Czy wiesz, dlaczego użycie @Transactional jest kluczowe dla Batchingu? (Podpowiedź: chodzi o to, że Hibernate nie może "grupować" INSERTów, jeśli każda transakcja ma być zatwierdzona oddzielnie).

Czy masz ochotę sprawdzić, co się dzieje, gdy zapomnisz o batch_size? Możesz na chwilę wykomentować te linie w application.properties i porównać czasy w teście – różnica przy 5000 rekordów będzie kolosalna.

Zaczynamy od wdrożenia batchingu w application.properties i przetestowania tego testem wydajnościowym? To absolutna podstawa dla "Mida", który musi dbać o bazę danych! 📈💾

