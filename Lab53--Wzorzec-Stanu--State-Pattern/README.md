Lab 53
------

Lab53--Wzorzec-Stanu--State-Pattern
-----------------------------------

Rekruter zapyta: 
"Mamy w systemie zamówienie. Może być w statusie NOWE, OPŁACONE, WYSŁANE lub ZWRÓCONE. 
Gdzie i jak napiszesz logikę, która pilnuje, żeby nie dało się wysłać nieopłaconego zamówienia?"

Odpowiedź Juniora: "Napiszę wielkiego switch-case w serwisie i będę rzucał wyjątki".
Odpowiedź Mida: "Użyję `Wzorca Stanu (State Pattern)`, albo maszyny stanów (State Machine)".

Dlaczego "State"?
-----------------
Eliminuje instrukcje if-else oparte na flagach statusu.
Obiekt zmienia swoje zachowanie w locie, w zależności od tego, w jakim jest aktualnie "stanie", 
a same stany pilnują, czy przejście do kolejnego jest dozwolone.

Krok 1: Interfejs Stanu
-----------------------

Każdy stan musi potrafić zareagować na akcje użytkownika. 
Jeśli akcja jest niedozwolona w danym stanie (np. próba zamknięcia transakcji, która nie jest jeszcze zweryfikowana), rzucamy błąd.

Stwórz `src/main/groovy/pl/edu/praktyki/state/TransactionState.groovy`:

```groovy
package pl.edu.praktyki.state

// Interfejs opisujący, co można zrobić z transakcją
interface TransactionState {
    void verify(StatefulTransaction tx)
    void process(StatefulTransaction tx)
    void cancel(StatefulTransaction tx)
}
```

Krok 2: Kontekst (Nasza Transakcja)
-----------------------------------

To jest obiekt, którego używa klient. 
Zauważ, że on sam nie ma żadnej logiki walidacji! 
Całą robotę deleguje do swojego aktualnego stanu (currentState).

Stwórz `src/main/groovy/pl/edu/praktyki/state/StatefulTransaction.groovy`:

```groovy
package pl.edu.praktyki.state

class StatefulTransaction {
String id
// Transakcja zawsze zaczyna jako "Nowa"
TransactionState currentState = new NewState()

    void verify() {
        currentState.verify(this)
    }

    void process() {
        currentState.process(this)
    }

    void cancel() {
        currentState.cancel(this)
    }
}
```

Krok 3: Konkretne Stany (Reguły biznesowe)
------------------------------------------

Teraz tworzymy klasy dla każdego statusu. 
To one decydują, w jaki stan przejdzie transakcja.

Stwórz plik `src/main/groovy/pl/edu/praktyki/state/States.groovy`
(możesz wrzucić wszystkie 3 klasy do jednego pliku dla wygody):

```groovy
package pl.edu.praktyki.state

class NewState implements TransactionState {
    @Override
    void verify(StatefulTransaction tx) {
        println ">>> Weryfikacja pozytywna. Zmieniam stan na: Verified."
        tx.currentState = new VerifiedState() // ZMIANA STANU
    }

    @Override
    void process(StatefulTransaction tx) {
        throw new IllegalStateException("Nie można przetworzyć NOWEJ transakcji. Najpierw zweryfikuj!")
    }

    @Override
    void cancel(StatefulTransaction tx) {
        println ">>> Anulowano nową transakcję."
        tx.currentState = new CancelledState()
    }
}

class VerifiedState implements TransactionState {
    @Override
    void verify(StatefulTransaction tx) {
        println ">>> Transakcja już jest zweryfikowana." // Ignorujemy
    }

    @Override
    void process(StatefulTransaction tx) {
        println ">>> Przetwarzanie zakończone sukcesem. Transakcja opłacona."
        // W prawdziwym życiu tu wołalibyśmy np. Payment API
    }

    @Override
    void cancel(StatefulTransaction tx) {
        println ">>> Anulowano zweryfikowaną transakcję. Wysyłam powiadomienie."
        tx.currentState = new CancelledState()
    }
}

class CancelledState implements TransactionState {
    @Override
    void verify(StatefulTransaction tx) { throw new IllegalStateException("Transakcja anulowana.") }

    @Override
    void process(StatefulTransaction tx) { throw new IllegalStateException("Transakcja anulowana.") }

    @Override
    void cancel(StatefulTransaction tx) { println ">>> Już jest anulowana." }
}
```

Krok 4: Test Spock (Udowadniamy cykl życia)
-------------------------------------------

Zobacz, jak pięknie testuje się ten wzorzec. 
Błędy rzucane są w przewidywalnych momentach, a logika jest całkowicie odporna na nieprawidłową kolejność operacji.

Stwórz `src/test/groovy/pl/edu/praktyki/state/StateSpec.groovy`:

```groovy
package pl.edu.praktyki.state

import spock.lang.Specification

class StateSpec extends Specification {

    def "nie można przetworzyć transakcji przed jej weryfikacją"() {
        given: "zupełnie nowa transakcja (NewState)"
        def tx = new StatefulTransaction(id: "T1")

        when: "próbujemy ją przetworzyć z pominięciem weryfikacji"
        tx.process()

        then: "powinna zablokować akcję rzucając błąd z NewState"
        def ex = thrown(IllegalStateException)
        ex.message.contains("Najpierw zweryfikuj")
    }

    def "prawidłowy cykl (Happy Path) powinien zadziałać"() {
        given: "nowa transakcja"
        def tx = new StatefulTransaction(id: "T2")

        when: "weryfikujemy"
        tx.verify()

        then: "stan to VerifiedState"
        tx.currentState instanceof VerifiedState

        when: "procesujemy (płacimy)"
        tx.process()

        then: "przechodzi bez błędów"
        noExceptionThrown()
    }

    def "anulowana transakcja staje się zablokowana"() {
        given:
        def tx = new StatefulTransaction(id: "T3")
        
        when: "anulujemy na samym starcie"
        tx.cancel()
        
        and: "próbujemy zweryfikować"
        tx.verify()

        then: "jest to zablokowane przez CancelledState"
        thrown(IllegalStateException)
    }
}
```

Tłumaczenie dla Rekrutera:

Dlaczego użyłeś wzorca Stanu, a nie `enuma Status` i instrukcji switch?

"Kiedy obiekt (np. Zamówienie lub Transakcja) ma skomplikowany cykl życia, logika typu switch(status) prowadzi do gigantycznych klas, 
które łamią zasadę `Single Responsibility`.

Dzięki wzorcowi Stanu, jeśli dodamy nowy krok w systemie (np. 'Wstrzymana do wyjaśnienia' - SuspendedState), 
po prostu dodaję jedną nową klasę. Nie muszę dopisywać kolejnych if-ów w istniejących metodach process() czy verify(). 
Obiekt Kontekstu po prostu oddelegowuje akcję do swojego aktualnego Stanu, a Stan wie, co ma z tym zrobić."

Zaimplementuj to i odpal testy! Jeśli chcesz jeszcze jedno klasyczne pytanie rekrutacyjne, 
mam w zanadrzu architektoniczne podejście `Lab54--Command-Pattern--Polecenie` albo `Lab54--wzorzec-Fasady--Facade`.
