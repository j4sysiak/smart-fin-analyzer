Lab 46
------

Poznaliśmy Łańcuch Zobowiązań i Dekorator.
Teraz czas na coś, co pozwala na budowanie skomplikowanych obiektów bez tworzenia gigantycznych konstruktorów.

To Lab 35: Wzorzec Builder (Budowniczy) z adnotacją @Builder.
-------------------------------------------------------------

W Javie wzorzec Builder to klasyk, ale wymaga pisania ogromnej ilości kodu (tzw. "boilerplate'u"). 
Klasa wewnętrzna, kopiowanie każdego pola, metody withName, withAmount zwracające this i metoda build() na końcu. 
Koszmar, jeśli masz klasę z 20 polami. 
Oczywiście w Javie można użyć biblioteki Lombok, ale w Groovym mamy to wbudowane w język!

Dlaczego Builder?

Kiedy klasa ma dużo pól, a zwłaszcza wiele pól opcjonalnych, tworzenie obiektu przez konstruktor staje się nieczytelne.

```groovy
// Słabo: Co to jest true, a co false?
def tx = new Transaction("T1", 500.0, "PLN", null, "Opis", true, false, null)

// Dobrze (Map constructor - Groovy style):
def tx = new Transaction(id: "T1", amount: 500.0, currency: "PLN", isVerified: true)


// Najlepiej (Builder Pattern):
def tx = Transaction.builder()
.id("T1")
.amount(500.0)
.currency("PLN")
.isVerified(true)
.build()
```


Uwaga: 
W Groovym "Map constructor" (środkowy przykład) często wystarcza, ale Builder daje dodatkowe bezpieczeństwo 
(można łatwo wprowadzić logikę walidacji w trakcie budowania) oraz pełne wsparcie dla autouzupełniania w IDE (IntelliJ).

Krok 1: Włączenie Buildera w Klasie Domenowej
---------------------------------------------

To jest niesamowicie proste. Używamy adnotacji `@groovy.transform.builder.Builder`.

Stwórz nowy model dla odmiany. Niech to będzie klasa `ReportConfig.groovy` (np. w pl.edu.praktyki.domain):

```groovy
package pl.edu.praktyki.domain

import groovy.transform.builder.Builder
import groovy.transform.ToString
import java.time.LocalDate

@Builder // TA ADNOTACJA GENERUJE CAŁEGO BUILDERA!
@ToString(includeNames = true)
class ReportConfig {
    String title
    LocalDate startDate
    LocalDate endDate
    boolean includeCharts
    String exportFormat
    String recipientEmail
}
```

Krok 2: Test Spock (BuilderSpec.groovy)
---------------------------------------

Zobaczymy, jak łatwo tworzy się teraz ten skomplikowany obiekt.

Stwórz `src/test/groovy/pl/edu/praktyki/domain/BuilderSpec.groovy`:

```groovy
package pl.edu.praktyki.domain

import spock.lang.Specification
import java.time.LocalDate

class BuilderSpec extends Specification {

    def "powinien zbudować obiekt ReportConfig używając wygenerowanego Buildera"() {
        when: "używamy Buildera (wygenerowanego przez AST w czasie kompilacji)"
        def config = ReportConfig.builder()
            .title("Raport Roczny 2025")
            .startDate(LocalDate.of(2025, 1, 1))
            .endDate(LocalDate.of(2025, 12, 31))
            .includeCharts(true)
            .exportFormat("PDF")
            // recipientEmail celowo pomijamy - będzie null
            .build()

        then: "obiekt jest poprawnie zbudowany"
        config.title == "Raport Roczny 2025"
        config.includeCharts == true
        config.recipientEmail == null // Wartość domyślna

        and: "toString ładnie wypisuje zawartość"
        println config.toString()
        config.toString().contains("PDF")
    }
}
```

Różne Strategie Buildera (Magia Groovy)
To, co pokazałem powyżej, to klasyczny builder (strategia domyślna: SimpleStrategy / DefaultStrategy). 
Ale Groovy potrafi więcej.

Strategia "Initializer" (Wzorzec dla stałych obiektów - Immutability)

Często chcemy, żeby nasz obiekt po zbudowaniu był niemutowalny (nie dało się zmienić jego pól).

Dodajmy do `ReportConfig` drugiego buildera! Zmień plik ReportConfig.groovy:

```groovy
package pl.edu.praktyki.domain

import groovy.transform.builder.Builder
import groovy.transform.builder.InitializerStrategy // IMPORT!
import groovy.transform.ToString
import java.time.LocalDate

// Domyślny builder z metodą .build() (jak w Lomobku)
@Builder 
// Drugi, specjalny builder (Tworzy obiekty przez mapę atrybutów lub specyficzny konstruktor)
@Builder(builderClassName = 'ConfigInitializer', builderMethodName = 'createInitializer', strategy = InitializerStrategy)
@ToString(includeNames = true)
class ReportConfig {
    final String title // FINAL - nie da się tego zmienić po utworzeniu!
    final LocalDate startDate
    final String exportFormat
    // ... reszta pól (pominąłem dla zwięzłości) ...
}
```
 

(Uwaga: w prawdziwym kodzie zazwyczaj wybierasz jedną ze strategii, nie dwie).

Dlaczego to jest ważne? (Perspektywa Mida)

Immutability (Niezmienność): W programowaniu współbieżnym (wielowątkowym) obiekty final są Twoim najlepszym przyjacielem, bo nie musisz się martwić o to, że jeden wątek zmieni stan, podczas gdy drugi go czyta. Wzorzec Builder to najwygodniejszy sposób tworzenia skomplikowanych obiektów niemutowalnych.

Czystość kodu (Zero Boilerplate): W Javie bez Lomboka musiałbyś napisać co najmniej 50-60 linijek kodu do samego Buildera.

Czytelność interfejsu (Fluent Interface): Zapis kaskadowy (chaining) z użyciem metod to po prostu wygodne w czytaniu i pisaniu.

Twoje zadanie!

Stwórz klasę ReportConfig z adnotacją @Builder.

Skopiuj test z Kroku 2.

Odpal test i upewnij się, że obiekt się buduje.

Zadanie dodatkowe (Mini-Wyzwanie):
Do klasy ReportConfig dodaj metodę boolean isValid(), która zwróci true tylko jeśli startDate jest przed endDate. Następnie w teście Spock zbuduj konfigurację z odwrotnymi datami i sprawdź, czy Twoja metoda poprawnie wyłapie błąd (!config.isValid()).

Daj znać, czy ten Wzorzec Projektowy przypadł Ci do gustu! Mamy w obwodzie jeszcze ciekawe rzeczy!

Sources
help
mytectra.com
slideshare.net
Google Search Suggestions
Display of Search Suggestions is required when using Grounding with Google Search. Learn more
"builder pattern groovy"