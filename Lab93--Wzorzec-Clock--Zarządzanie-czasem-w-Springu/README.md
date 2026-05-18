Lab93
-----

Lab93--Wzorzec-Clock--Zarządzanie-czasem-w-Springu
==================================================

Cel labu
--------

W tym labie usunęliśmy ukrytą zależność od czasu systemowego (`LocalDate.now()`) z logiki biznesowej i zastąpiliśmy ją kontrolowanym zegarem (`java.time.Clock`) wstrzykiwanym przez Springa.

Dzięki temu:
- logika budżetowa jest deterministyczna,
- testy nie zależą od "dzisiejszej daty",
- można symulować koniec miesiąca i przejście do kolejnego miesiąca bez czekania.

---

Problem
-------

Wcześniej `BudgetService` używał:

```groovy
def today = LocalDate.now()
```

To powoduje, że testy logiki budżetowej są niestabilne i zależne od daty uruchomienia.

Przykład problemu: test przechodzi w marcu, ale pada w kwietniu, mimo że kod się nie zmienił.

---

Implementacja
-------------

### 1) Bean zegara w konfiguracji

Plik: `src/main/groovy/pl/edu/praktyki/config/AppConfig.groovy`

```groovy
@Configuration
class AppConfig {
    @Bean
    Clock clock() {
        return Clock.systemDefaultZone()
    }
}
```

To jest domyślny zegar produkcyjny.

### 2) Refaktoryzacja `BudgetService`

Plik: `src/main/groovy/pl/edu/praktyki/service/BudgetService.groovy`

- dodano `@Autowired Clock clock`
- zamieniono `LocalDate.now()` na `LocalDate.now(clock)`

Kluczowy fragment:

```groovy
def today = LocalDate.now(clock)
def start = today.withDayOfMonth(1)
def end = today.withDayOfMonth(today.lengthOfMonth())
def transactions = txRepo.findByCategoryEntityAndDateBetween(category, start, end)
```

Efekt: logika budżetu działa tak samo, ale czas jest sterowalny z testów.

---

Testy Time Travel
-----------------

### 1) Test stałej daty (`Clock.fixed`)

Plik: `src/test/groovy/pl/edu/praktyki/service/TimeTravelBudgetSpec.groovy`

- `@TestConfiguration` + `@Primary` podmieniają Bean `Clock` na stały punkt czasu,
- test wymusza marzec 2026,
- sprawdza, że do limitu liczą się transakcje tylko z tego miesiąca.

### 2) Test granicy miesiąca (`MutableClock`)

Plik: `src/test/groovy/pl/edu/praktyki/service/TimeTravelMonthBoundarySpec.groovy`

- w teście użyty jest sterowalny zegar (`MutableClock`),
- najpierw czas ustawiony na 31 marca,
- potem "przeskok" na 1 kwietnia,
- asercja potwierdza, że marcowe wydatki nie wpływają na kwietniowy limit.

To testuje realny przypadek biznesowy: reset limitu miesięcznego przy zmianie miesiąca.

---

Jak uruchomić
-------------

```powershell
.\gradlew.bat test --tests "pl.edu.praktyki.service.TimeTravelBudgetSpec" --no-daemon -i
.\gradlew.bat test --tests "pl.edu.praktyki.service.TimeTravelMonthBoundarySpec" --no-daemon -i
```

Oba testy razem:

```powershell
.\gradlew.bat test --tests "pl.edu.praktyki.service.TimeTravel*" --no-daemon -i
```

---

Wniosek architektoniczny
------------------------

Wzorzec Clock eliminuje "niewidzialną" zależność od czasu systemowego i znacząco poprawia testowalność.

To podejście jest standardem w kodzie produkcyjnym dla:
- limitów miesięcznych,
- harmonogramów,
- okresów rozliczeniowych,
- reguł zależnych od daty.

---

Wartość na rozmowę rekrutacyjną
-------------------------------

Krótka odpowiedź:

> "Nie używam `LocalDate.now()` bezpośrednio w logice biznesowej. Wstrzykuję `java.time.Clock` jako Bean Springa i używam `LocalDate.now(clock)`. W testach podmieniam zegar na `Clock.fixed()` albo sterowalny zegar, dzięki czemu mogę symulować koniec miesiąca i sprawdzić logikę limitów bez czekania na realny upływ czasu."

To pokazuje dojrzałość inżynierską i myślenie o jakości testów.

---

Podsumowanie
------------

Lab93 domknął temat testowalności logiki czasowej:
- produkcja: systemowy Clock,
- testy: fixed/mutable Clock,
- biznes: poprawne liczenie limitów per miesiąc,
- jakość: deterministyczne testy bez flakowania.
