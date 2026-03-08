Lab 41
------

To jest absolutny "must-have" dla każdego Seniora/Mida pracującego z aplikacjami asynchronicznymi 
(np. Spring Boot, systemy kolejkowe, eventy).

Problem "Flaky Tests"

Większość programistów testuje asynchroniczność w ten sposób:

```groovy
def "test"() {
    when: service.processAsync()
    then: sleep(2000) // Czekaj 2 sekundy (naiwne i powolne!)
    and: repo.exists()
}
```
To jest bardzo złe podejście, ponieważ:
- Testy są powolne (niepotrzebne czekanie)
- Testy są niestabilne (czasami 2 sekundy to za mało, a czasami to za dużo)
- Nie testujemy faktycznie asynchroniczności, tylko zakładamy, że po 2 sekundach wszystko będzie gotowe
- Nie mamy kontroli nad tym, co się dzieje w międzyczasie (np. inne testy mogą spowolnić ten test)
- Nie możemy łatwo debugować testów (nie wiemy, czy problem jest w kodzie, czy w czasie oczekiwania)
- Nie możemy łatwo testować różnych scenariuszy (np. co się dzieje, gdy proces trwa dłużej niż 2 sekundy)
- Nie możemy łatwo testować różnych warunków (np. co się dzieje, gdy repo jest niedostępne)
- Nie możemy łatwo testować różnych wyników (np. co się dzieje, gdy repo zwraca błąd)
- Nie możemy łatwo testować różnych konfiguracji (np. co się dzieje, gdy mamy różne ustawienia timeoutu)
- Nie możemy łatwo testować różnych środowisk (np. co się dzieje, gdy testujemy na różnych maszynach)
- Nie możemy łatwo testować różnych wersji (np. co się dzieje, gdy mamy różne wersje biblioteki)
- Nie możemy łatwo testować różnych danych (np. co się dzieje, gdy mamy różne dane wejściowe)
- Nie możemy łatwo testować różnych użytkowników (np. co się dzieje, gdy mamy różne role)
- Nie możemy łatwo testować różnych scenariuszy (np. co się dzieje, gdy mamy różne scenariusze biznesowe)
- Nie możemy łatwo testować różnych przypadków (np. co się dzieje, gdy mamy różne przypadki użycia)
- Nie możemy łatwo testować różnych warunków brzegowych (np. co się dzieje, gdy mamy różne warunki brzegowe)
- Nie możemy łatwo testować różnych scenariuszy awaryjnych (np. co się dzieje, gdy mamy różne
  
To zły wzorzec! Jeśli system zadziała w 100ms, marnujesz 1.9s. 
Jeśli potrzebuje 2.1s, test wywali się na zielono tylko przez przypadek. 
`Awaitility` pozwala nam czekać dynamicznie ("tak długo, aż warunek będzie spełniony, ale nie dłużej niż X").

Krok 1: Dodaj Awaitility do build.gradle
----------------------------------------

```groovy
dependencies {
 
// Dodaj Awaitility do testów
testImplementation 'org.awaitility:awaitility-groovy:4.2.0'
}
```

Krok 2: Serwis z asynchroniczną logiką
--------------------------------------

Symulujemy sytuację, w której metoda wraca natychmiast, a praca (np. zapis do bazy) odbywa się w tle z opóźnieniem.
Stwórz `src/main/groovy/pl/edu/praktyki/service/AsyncService.groovy`:

```groovy
package pl.edu.praktyki.service

import org.springframework.stereotype.Service
import java.util.concurrent.ConcurrentHashMap

@Service
class AsyncService {
    // Symulacja bazy danych (ConcurrentHashMap jest bezpieczna wątkowo)
    def db = new ConcurrentHashMap<String, String>()

    void performActionAsync(String id, String value) {
        Thread.start {
            sleep(1500) // Praca w tle zajmuje 1.5 sekundy
            db[id] = value
        }
    }

    boolean exists(String id) {
        return db.containsKey(id)
    }
}
```

Krok 3: Test z Awaitility (AsyncServiceSpec.groovy)
---------------------------------------------------

Zauważ, jak elegancko używamy await() – test sprawdzi bazę danych natychmiast po pojawieniu się rekordu.
Stwórz `src/test/groovy/pl/edu/praktyki/service/AsyncServiceSpec.groovy`:

```groovy
package pl.edu.praktyki.service

import spock.lang.Specification
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import static org.awaitility.Awaitility.await
import static java.util.concurrent.TimeUnit.SECONDS

@SpringBootTest(classes = [AsyncService])
class AsyncServiceSpec extends Specification {

    @Autowired AsyncService asyncService

    def "powinien poczekać aż rekord pojawi się w bazie asynchronicznie"() {
        when: "wywołujemy metodę w tle"
        asyncService.performActionAsync("TX-1", "PROCESSED")

        then: "używamy await, aby czekać na pojawienie się rekordu"
        // await() sprawdza warunek wielokrotnie, dopóki nie przejdzie
        // Jeśli nie przejdzie w ciągu 3 sekund, test oblewa.
        await().atMost(3, SECONDS).until {
            asyncService.exists("TX-1")
        }
        
        and: "dodatkowa weryfikacja wartości"
        asyncService.db["TX-1"] == "PROCESSED"
    }
}
```

Dlaczego to jest "Mid-level/Senior Practice"?

Determinism (Determinizm): 
Twoje testy będą zawsze tak szybkie, jak pozwala na to system. 
Jeśli metoda potrzebuje 100ms, test skończy się po 100ms. 
Jeśli potrzebuje 1.5s, skończy się po 1.5s. 
Nie ustawiasz sztywnych sleep().

Robustness (Stabilność): 
Eliminuje tzw. Flaky Tests (testy, które losowo padają na buildach CI/CD). 
To najczęstsza przyczyna frustracji w zespołach DevOps.

Clean Code: 
Kod testu staje się czytelniejszy – piszesz "czekaj aż istnieje", a nie "śpij i módl się".



----------------------------------------------------------------------------------------
######################  drugie podejście: Mockowanie repozytorium ######################
----------------------------------------------------------------------------------------

To jest doskonały przykład na to, jak bardzo szybko testować kod, który normalnie wymagałby całej bazy danych. 
Dzięki `Mock(TransactionRepository)` nie musisz uruchamiać H2, robić migracji Flywayem ani czekać na start Springa.

Oto jak to zrobić "po mistrzowsku".

Krok 1: Przygotuj serwis (`AsyncService.groovy`)
------------------------------------------------

Aby móc wstrzyknąć repozytorium ręcznie (w konstruktorze), 
musisz dodać pole w serwisie i upewnić się, że Spring też będzie wiedział jak je tam włożyć (używając @Autowired lub konstruktora).

```groovy
package pl.edu.praktyki.service

import org.springframework.stereotype.Service
import org.springframework.beans.factory.annotation.Autowired
import pl.edu.praktyki.repository.TransactionRepository
import pl.edu.praktyki.domain.Transaction

@Service
class AsyncService {

    // Spring wstrzyknie to automatycznie w aplikacji
    @Autowired 
    TransactionRepository transactionRepository

    // Dodajemy konstruktor, który pozwala wstrzyknąć repozytorium ręcznie (dla testów!)
    AsyncService() {}
    AsyncService(TransactionRepository transactionRepository) {
        this.transactionRepository = transactionRepository
    }

    void saveTransaction(Transaction tx) {
        // Logika biznesowa
        transactionRepository.save(tx)
    }
}
```

Krok 2: Test Spock (AsyncServiceMockSpec.groovy)
------------------------------------------------

Teraz przetestujemy serwis bez podnoszenia bazy danych. 
Zastosujemy tu tzw. "Interaction Testing" – czyli sprawdzimy, czy serwis w ogóle wywołał metodę save na repozytorium.

```groovy
package pl.edu.praktyki.service

import spock.lang.Specification
import pl.edu.praktyki.repository.TransactionRepository
import pl.edu.praktyki.domain.Transaction

class AsyncServiceMockSpec extends Specification {

    // 1. Tworzymy MOCKA repozytorium
    def repoMock = Mock(TransactionRepository)

    // 2. Wstrzykujemy mocka przez konstruktor (ręczny DI)
    def service = new AsyncService(repoMock)

    def "powinien wywołać save na repozytorium przy zapisie"() {
        given: "transakcja"
        def tx = new Transaction(id: "TEST-1", amount: 100)

        when: "wywołujemy metodę zapisu"
        service.saveTransaction(tx)

        then: "weryfikujemy, że metoda save na repozytorium została wywołana dokładnie raz"
        1 * repoMock.save(_) // Sprawdzamy interakcję!
    }
}
```

Dlaczego to jest "Must-Know" dla Mid-Developera?

Testy Jednostkowe vs Integracyjne:

Jeśli testujesz bazę – używasz `@SpringBootTest (test integracyjny)`.

Jeśli testujesz logikę biznesową (np. przeliczenie walut, czy wywołanie odpowiedniej metody repo) 
              – używasz Mock (test jednostkowy).

Testy jednostkowe powinny stanowić ok. 80% Twoich testów, bo są mega szybkie.

`1 * repoMock.save(_)`: 
To jest czysta poezja Spocka. 
Mówisz testowi: 
"Nie obchodzi mnie jak działa baza, nie obchodzi mnie Hibernate. 
Interesuje mnie tylko, czy mój serwis poprawnie wysłał dane do repozytorium" - czyli czy metoda `save` została wywołana dokładnie raz.


Refaktoryzacja dla testowalności: 
Zauważ, że musieliśmy dodać konstruktor:  `AsyncService(TransactionRepository r) { ... }`. 

To jest złota zasada: 
kod jest łatwy do przetestowania tylko wtedy, gdy jest dobrze zaprojektowany. 
Jeśli musisz używać @Autowired na polach (field injection), testowanie staje się trudniejsze. 
Wstrzykiwanie przez konstruktor (Constructor Injection) to wzorzec, który zawsze wygrywa.



