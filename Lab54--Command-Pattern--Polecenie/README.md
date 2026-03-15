Lab 54
------

Lab54--Command-Pattern--Polecenie
---------------------------------

`Wzorzec Polecenie (Command Pattern)` to absolutna klasyka. 
Kiedy na rozmowie o pracę ktoś zapyta Cię: "Jak zaimplementujesz w systemie opcję:
 1. 'Cofnij' (Undo)?"
 2. "Jak zaprojektujesz system, który zapisuje operacje do wykonania w nocy (w kolejce)?"

  to jedyną poprawną odpowiedzią jest właśnie `wzorzec Command`.

Dlaczego "Command"?
-------------------

Zamiast bezpośrednio wywoływać metodę np. `konto.odejmij(100)`, pakujesz tę intencję w obiekt np. `new WithdrawCommand(konto, 100)`.
Ponieważ operacja jest teraz obiektem, możesz ją:
1. Kolejkowanie operacji do listy (Historia). 
2. Wykonać później (Kolejkowanie / Kafka).
3. Cofnąć (Undo) – bo obiekt "pamięta", co dokładnie zrobił.

Krok 1: Odbiorca (Receiver) i Interfejs Polecenia
-------------------------------------------------

Zaczynamy od konta bankowego (które po prostu przechowuje stan) oraz interfejsu dla naszych poleceń.

Stwórz plik `src/main/groovy/pl/edu/praktyki/command/BankAccount.groovy`:

```groovy
package pl.edu.praktyki.command

// 1. RECEIVER: Głupi obiekt, który tylko wykonuje proste instrukcje
class BankAccount {
String accountId
BigDecimal balance = 0.0

    void add(BigDecimal amount) { balance += amount }
    void subtract(BigDecimal amount) { balance -= amount }
}
```


Stwórz plik interface: `src/main/groovy/pl/edu/praktyki/command/BankCommand.groovy`:

```groovy
package pl.edu.praktyki.command

// 2. INTERFEJS POLECENIA: Każde polecenie musi umieć się wykonać i... COFNĄĆ!
interface BankCommand {
    void execute()
    void undo()
}
```

Krok 2: Konkretne Polecenia (Concrete Commands)
-----------------------------------------------

Teraz tworzymy obiekty reprezentujące konkretne akcje. 
Zauważ, że obiekt "wie", na jakim koncie ma operować i jaką kwotą dysponuje.

Stwórz plik `src/main/groovy/pl/edu/praktyki/command/BankCommand.groovy`:

```groovy
package pl.edu.praktyki.command

import groovy.transform.Canonical

@Canonical
class DepositCommand implements BankCommand {
BankAccount account
BigDecimal amount

    @Override
    void execute() {
        println ">>>[WYKONUJĘ] Wpłata $amount na konto ${account.accountId}"
        account.add(amount)
    }

    @Override
    void undo() {
        println "<<< [COFAM] Anulowanie wpłaty $amount z konta ${account.accountId}"
        // Cofnięciem wpłaty jest wypłata!
        account.subtract(amount)
    }
}
```


Stwórz plik `src/main/groovy/pl/edu/praktyki/command/WithdrawCommand.groovy`:

```groovy
package pl.edu.praktyki.command

import groovy.transform.Canonical

@Canonical
class WithdrawCommand implements BankCommand {
    BankAccount account
    BigDecimal amount

    @Override
    void execute() {
        println ">>> [WYKONUJĘ] Wypłata $amount z konta ${account.accountId}"
        account.subtract(amount)
    }

    @Override
    void undo() {
        println "<<< [COFAM] Anulowanie wypłaty $amount na konto ${account.accountId}"
        // Cofnięciem wypłaty jest wpłata!
        account.add(amount)
    }
}
```


Krok 3: Wywoływacz (Invoker / CommandManager)
---------------------------------------------
To on zarządza ruchem. 
Przyjmuje polecenia, wykonuje je i zapisuje na stosie (historii), aby w każdej chwili móc je cofnąć.

Stwórz plik `src/main/groovy/pl/edu/praktyki/command/CommandManager.groovy`:

```groovy
package pl.edu.praktyki.command

class CommandManager {
// Historia wykonanych operacji (działa jak Stos / Stack)
private List<BankCommand> history =[]

    // Wykonuje polecenie i zapisuje je w historii, by móc je później cofnąć
    void executeCommand(BankCommand command) {
        command.execute()
        history << command // dodajemy na koniec listy
    }

   // Cofa ostatnio wykonane polecenie
   void undoLast() {
      if (history.isEmpty()) {
         println "<<< [BŁĄD] Brak operacji do cofnięcia!"
         return
      }

      BankCommand lastCommand = history.removeLast() // <-- TO JEST KLUCZ (bierze/usuwa z listy ostatnia operację)!
      lastCommand.undo()
   }
}
```

Krok 4: Test Spock (Udowadniamy działanie Undo)
-----------------------------------------------

To jest ten moment, w którym Wzorzec Polecenia pokazuje swoją prawdziwą moc. 
Składamy serię operacji, a następnie krok po kroku je cofamy, 
obserwując jak stan konta "cofa się w czasie".

Stwórz plik `src/test/groovy/pl/edu/praktyki/command/CommandSpec.groovy`:

```groovy
package pl.edu.praktyki.command

import spock.lang.Specification

class CommandSpec extends Specification {

    def "powinien poprawnie wykonywać i cofać operacje bankowe"() {
        given: "konto początkowe i manager poleceń"
        def account = new BankAccount(accountId: "PL-999", balance: 1000.0)
        def manager = new CommandManager()

        when: "wykonujemy dwie operacje: wpłata 500 i wypłata 200"
        manager.executeCommand(new DepositCommand(account, 500.0))
        manager.executeCommand(new WithdrawCommand(account, 200.0))

        then: "bilans to 1300 (1000 + 500 - 200)"
        account.balance == 1300.0

        when: "cofamy OSTATNIĄ operację (czyli wypłatę 200)"
        manager.undoLast()

        then: "bilans wraca do 1500 (1000 + 500)"
        account.balance == 1500.0

        when: "cofamy KOLEJNĄ operację (czyli wpłatę 500)"
        manager.undoLast()

        then: "bilans wraca dokładnie do stanu początkowego!"
        account.balance == 1000.0
        
        and: "historia poleceń jest teraz pusta"
        manager.history.isEmpty()
    }
}
```

Tłumaczenie dla Rekrutera (Dlaczego to jest poziom Mid/Senior?):

Gdy ktoś zapyta Cię: "Po co tworzyć aż 4 klasy (Interface, Manager, Deposit, Withdraw), skoro mogłem po prostu napisać konto.balance += 500?"
Twoja odpowiedź jako inżyniera:

1. Opcja Cofania (Undo / Rollback): Bez wzorca Command zrobienie funkcji "Cofnij" dla użytkownika (np. Ctrl+Z w edytorze tekstu) jest koszmarem. 
   Z Command to po prostu zawołanie history.pop().undo().

2. Kolejkowanie Operacji: Mając operację jako Obiekt (DepositCommand), nie musisz jej wykonywać od razu! 
   Możesz ją zapisać do bazy danych, wysłać przez Kafkę / RabbitMQ na inny serwer, a tamten serwer odczyta z niej parametry 
   i dopiero zawoła execute(). 
   Zwykłego wywołania metody konto.add() nie wyślesz przez sieć.

3. To fundament CQRS i Event Sourcingu: W nowoczesnych mikroserwisach bankowych stan konta to nie jest po prostu jedna liczba w bazie. 
   Stan konta to suma wszystkich historycznych poleceń (Zdarzeń), które się na nim wykonały.

Wdróż te klocki i odpal test! Zobacz w konsoli, jak system elegancko żongluje wpłatami i wypłatami w jedną i drugą stronę.



Podsumowanie:
-------------

Tekst jest napisany tak, aby pokazać rekruterom lub innym deweloperom, że rozumiesz nie tylko jak napisać wzorzec, 
ale przede wszystkim dlaczego się go stosuje w architekturze klasy Enterprise.

Wklej poniższy fragment do sekcji z wzorcami architektonicznymi (np. pod opisem AOP lub Traits):

```text
### ⏪ Command Pattern (Wzorzec Polecenia) & Undo Mechanism

W projekcie zaimplementowano wzorzec **Command**, aby w pełni kontrolować cykl życia operacji finansowych (np. wpłaty i wypłaty). Zamiast bezpośrednio modyfikować stan obiektów (co prowadzi do silnego sprzężenia i trudności w śledzeniu historii), każda operacja jest hermetyzowana w postaci niezależnego obiektu.

#### Kluczowe cechy implementacji:
*   **Hermetyzacja intencji:** Operacje biznesowe takie jak `DepositCommand` i `WithdrawCommand` implementują wspólny interfejs, zamykając w sobie logikę wykonania (`execute`) oraz odwrócenia skutków (`undo`).
*   **Mechanizm Cofania (Rollback/Undo):** `CommandManager` (Invoker) przechowuje historię wykonanych operacji w formie stosu (LIFO). Wykorzystanie nowoczesnego API kolekcji (`removeLast()`) gwarantuje bezbłędne cofanie transakcji w czasie, chroniąc system przed anomaliami strumieni FIFO.
*   **Fundament pod Event Sourcing:** Obiekty poleceń są całkowicie odseparowane od momentu ich wywołania. Taka architektura stanowi idealną bazę pod systemy asynchroniczne (np. Kafka, RabbitMQ) oraz architekturę CQRS, gdzie polecenia mogą być serializowane, kolejkowane i odtwarzane po awarii systemu. 

```

Co ten opis mówi o Tobie jako programiście?
-------------------------------------------

1. Używasz słów kluczy, które rekruterzy kochają: Hermetyzacja, Silne/Słabe sprzężenie (Coupling), LIFO, CQRS, Event Sourcing.
2. Pokazujesz, że wzorzec projektowy to dla Ciebie nie jest sztuka dla sztuki, ale konkretne rozwiązanie biznesowe (Rollback transakcji).
3. Świadomie zarządzasz strukturami danych (wspomnienie o LIFO vs FIFO i wyborze odpowiedniej metody zdejmującej ze stosu).