# Lab94 - Transactional Event Listeners

## Temat
Spójność w systemach event-driven z użyciem Spring:
- `@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)`
- `@Async` dla odseparowania ciężkich side-effectów
- poprawne granice transakcji (proxy, self-invocation, `REQUIRES_NEW`)

## Cel Laba
Pokazać jak zapewnić, że listenery uruchamiają się dopiero po udanym commicie transakcji biznesowej oraz jak uniknąć pułapek AOP w Spring (self-invocation i `@Transactional` na metodach prywatnych).

---

## Co zrobiliśmy (krok po kroku)

### Krok 1-3: Ujednolicenie listenerów na AFTER_COMMIT
Zmieniono listenery eventu `TransactionBatchProcessedEvent`, aby działały po commicie:
- `src/main/groovy/pl/edu/praktyki/service/AuditEventListener.groovy`
- `src/main/groovy/pl/edu/praktyki/service/AsyncNotificationService.groovy`
- `src/main/groovy/pl/edu/praktyki/service/GlobalStatsProjector.groovy`

Docelowa adnotacja:
```groovy
@Async("bulkTaskExecutor")
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
```

### Krok 4: Naprawa transakcji projektora (proxy + REQUIRES_NEW)
Wykryty problem: `@Transactional` na `private` metodzie wywoływanej z tej samej klasy nie działa (self-invocation omija proxy).

Rozwiązanie:
- wydzielenie logiki zapisu projekcji do osobnego beana
  - `src/main/groovy/pl/edu/praktyki/service/GlobalSummaryUpdater.groovy`
- w nim publiczna metoda:
```groovy
@Transactional(propagation = Propagation.REQUIRES_NEW)
void updateGlobalSummary(TransactionBatchProcessedEvent event)
```
- `GlobalStatsProjector` tylko deleguje do `GlobalSummaryUpdater`

### Krok 5: Naprawa self-invocation w fasadzie
Wydzielono ciężką logikę transakcyjną z `SmartFinFacade` do osobnego beana:
- `src/main/groovy/pl/edu/praktyki/facade/SmartFinReportOrchestrator.groovy`

Wynik:
- `SmartFinFacade` zostaje lekkim entry-pointem (sync i async)
- `SmartFinReportOrchestrator.processAndGenerateReport(...)` jest transakcyjne
- event publikowany jest wewnątrz transakcji, a listenery odpalają po commicie

---

## Testy, które potwierdzają działanie

### Istniejące i uruchamiane
- `pl.edu.praktyki.async.GlobalStatsProjectorSpec`
- `pl.edu.praktyki.service.AsyncNotificationSpec`
- `pl.edu.praktyki.event.EventDecouplingSpec`
- `pl.edu.praktyki.facade.SmartFinFacadeAsyncSpec`

### Dodane w tym labie
- `src/test/groovy/pl/edu/praktyki/event/AfterCommitConsistencySpec.groovy`
  - COMMIT => listenery działają
  - ROLLBACK => listenery `AFTER_COMMIT` nie działają
- `src/test/groovy/pl/edu/praktyki/facade/SmartFinFacadeAsyncE2ESpec.groovy`
  - pełny E2E async przez `processInBackgroundTask`
- `src/test/groovy/pl/edu/praktyki/facade/SmartFinFacadeRollbackE2ESpec.groovy`
  - negatywny E2E: rollback => brak skutków ubocznych listenerów

---

## Jak uruchamiać testy (przykłady)
```powershell
cd C:\dev\smart-fin-analyzer
.\gradlew.bat "-Dspring.profiles.active=tc" "-Denable.flyway=true" test --tests "pl.edu.praktyki.event.AfterCommitConsistencySpec" --no-daemon --rerun-tasks
.\gradlew.bat "-Dspring.profiles.active=tc" "-Denable.flyway=true" test --tests "pl.edu.praktyki.facade.SmartFinFacadeAsyncE2ESpec" --no-daemon --rerun-tasks
.\gradlew.bat "-Dspring.profiles.active=tc" "-Denable.flyway=true" test --tests "pl.edu.praktyki.facade.SmartFinFacadeRollbackE2ESpec" --no-daemon --rerun-tasks
```

---

## Najważniejsze wnioski techniczne
- `@TransactionalEventListener(AFTER_COMMIT)` gwarantuje, że listener zobaczy tylko dane po commicie.
- `@Async` i `@TransactionalEventListener` można łączyć, ale trzeba pilnować granic transakcji.
- `@Transactional` działa przez proxy Springa - self-invocation psuje ten mechanizm.
- `@Transactional` na `private` metodzie wywołanej z tej samej klasy to antywzorzec.
- Dla izolacji zapisu projekcji można użyć `REQUIRES_NEW` w osobnym beanie.

---

## Jak odpowiadać na pytania rekrutera (Q&A)

### 1) "Po co `@TransactionalEventListener(AFTER_COMMIT)` zamiast `@EventListener`?"
**Odpowiedź:**
Bo `AFTER_COMMIT` gwarantuje, że listener odpali się dopiero po zatwierdzeniu transakcji. Dzięki temu side-effecty (np. notyfikacja, projekcja CQRS) nie uruchomią się dla danych, które finalnie zostały wycofane.

### 2) "Czym grozi zwykły listener uruchamiany w trakcie transakcji?"
**Odpowiedź:**
Może zobaczyć stan niezatwierdzony lub wykonać side-effect mimo późniejszego rollbacku. To powoduje niespójność między bazą a światem zewnętrznym.

### 3) "Dlaczego `@Transactional` na private metodzie nie zadziałał?"
**Odpowiedź:**
Spring AOP działa przez proxy i przechwytuje wywołania z zewnątrz beana. Wywołanie private/self-invocation wewnątrz tej samej klasy omija proxy, więc adnotacja nie jest zastosowana.

### 4) "Jak naprawiliście self-invocation?"
**Odpowiedź:**
Wydzieliliśmy logikę do osobnych beanów (`GlobalSummaryUpdater`, `SmartFinReportOrchestrator`) i delegujemy między beanami. Dzięki temu wywołanie przechodzi przez proxy i transakcje działają zgodnie z konfiguracją.

### 5) "Skąd pewność, że `REQUIRES_NEW` naprawdę działa?"
**Odpowiedź:**
Z testów i trace logów transakcyjnych (`JpaTransactionManager`): widać tworzenie nowej transakcji dla `GlobalSummaryUpdater.updateGlobalSummary` z `PROPAGATION_REQUIRES_NEW` oraz poprawny commit.

### 6) "Jak udowodniliście brak side-effectów po rollbacku?"
**Odpowiedź:**
Mamy test negatywny E2E (`SmartFinFacadeRollbackE2ESpec`): po kontrolowanym rollbacku nie ma zapisu transakcji, nie ma wzrostu projekcji `GLOBAL`, a listenery `AFTER_COMMIT` nie wykonują się.

---

## One-liner do opowiedzenia na rozmowie
"W tym labie ustabilizowałem event-driven consistency: listenery działają po commicie (`AFTER_COMMIT`), poprawiłem granice transakcji przez wydzielenie beanów (bez self-invocation), a poprawność potwierdziłem testami pozytywnymi i negatywnymi E2E." 
