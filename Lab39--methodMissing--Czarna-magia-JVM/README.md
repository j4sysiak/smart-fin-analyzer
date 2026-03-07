Lab 39
------

To jest moment, w którym przestajesz być „programistą piszącym kod”, a stajesz się „twórcą frameworków”. 
Mechanizm methodMissing pozwala stworzyć klasę, która jest „nieskończenie inteligentna” – reaguje na wywołania metod, których nie ma w kodzie źródłowym.

Lab 28: methodMissing – "Czarna magia" JVM
------------------------------------------

Cel: 
Stworzenie klasy `DynamicDataContainer`, która pozwala na dostęp do dowolnych pól i wywoływanie dowolnych "metod-akcji" na podstawie nazwy, 
bez definiowania ich wewnątrz klasy.

Krok 1: Implementacja DynamicDataContainer.groovy
-------------------------------------------------

Stwórz plik `src/main/groovy/pl/edu/praktyki/dynamic/DynamicDataContainer.groovy`.

```groovy
package pl.edu.praktyki.dynamic

class DynamicDataContainer {
    // Nasz wewnętrzny magazyn danych
    private Map<String, Object> data = [:]

    // 1. methodMissing: Przechwytuje wywołania nieistniejących metod
    def methodMissing(String name, args) {
        println ">>> Wykryto wywołanie nieistniejącej metody: $name z argumentami $args"

        // Jeśli metoda zaczyna się od "set", zapisujemy wartość
        if (name.startsWith("set")) {
            String property = name.substring(3).toLowerCase()
            data[property] = args[0]
            return "Zapisano $property = ${args[0]}"
        }

        // Jeśli metoda zaczyna się od "get", odczytujemy wartość
        if (name.startsWith("get")) {
            String property = name.substring(3).toLowerCase()
            return data[property]
        }

        throw new MissingMethodException(name, DynamicDataContainer, args)
    }

    // 2. propertyMissing: Przechwytuje odwołania do nieistniejących pól
    def propertyMissing(String name) {
        return data[name]
    }
}
```


Krok 2: Test Spock (DynamicSpec.groovy)
---------------------------------------

To jest test, który udowadnia, że klasa reaguje na metody, których... nie ma w pliku .groovy.
Stwórz `src/test/groovy/pl/edu/praktyki/dynamic/DynamicSpec.groovy`:

```groovy
package pl.edu.praktyki.dynamic

import spock.lang.Specification

class DynamicSpec extends Specification {

    def "powinien dynamicznie obsłużyć metody get/set"() {
        given: "nasz dynamiczny kontener"
        def container = new DynamicDataContainer()

        when: "wywołujemy metody, których nie ma w kodzie klasy"
        def saveResult = container.setName("Jan")
        def name = container.getName()

        then: "metody działają jak prawdziwe"
        saveResult == "Zapisano name = Jan"
        name == "Jan"
    }

    def "powinien obsługiwać dynamiczne właściwości"() {
        given:
        def container = new DynamicDataContainer()
        container.setName("Kowalski")

        expect:
        container.name == "Kowalski" // propertyMissing zadziałało!
    }
}
```

Dlaczego to jest "Senior/Architect Practice"?

Frameworki typu ORM (Hibernate/GORM): 

Kiedy piszesz user.findByLastName('Kowalski') w Grailsie, ta metoda nie istnieje. 

Framework przechwytuje ją przez methodMissing, parsuje nazwę, zamienia na zapytanie SQL i wykonuje. 
Ty właśnie stworzyłeś fundament takiej magii!

Wrappers / Proxies: 
To idealny sposób na tworzenie wrapperów wokół starych, brzydkich systemów (tzw. Legacy Systems). 
Zamiast pisać 100 metod dla każdego pola w starej bazie, piszesz jedną metodę methodMissing, która mapuje wszystko na żądanie.

Redukcja kodu: 
Pozwala usunąć całe pliki "boilerplate'u", gdzie musiałbyś definiować setki getterów/setterów.


Wyzwanie Finałowe Lab 39:
-------------------------

Twoim wyzwaniem jest stworzenie "Szybkiego Kalkulatora Operacji".
Dopisz do methodMissing obsługę metody `calculateSum(a, b)`.

Ale uwaga: 
Musisz obsłużyć dowolną liczbę argumentów. 
Wtyczka powinna przyjąć listę liczb (np. calculateSum(1, 2, 3, 4)) i zwrócić ich sumę.

Test Spocka powinien sprawdzić: 
`container.calculateSum(10, 20, 30) == 60`.

Podpowiedź: 
args w methodMissing to Object[]. 
Możesz je zsumować, zamieniając na listę: args.toList().sum().

Rozwiazanie:
------------

Oto rozwiązanie wyzwania. 
To zadanie pokazuje, jak methodMissing działa jak "łapacz" (trap) dla wszystkich wywołań, które nie istnieją w kodzie klasy. 
Dzięki temu możesz stworzyć API, które jest niezwykle elastyczne.

1. Implementacja w `DynamicDataContainer.groovy`
   Dopisz do metody methodMissing obsługę dynamicznego sumowania. 
   Użyjemy wbudowanej w Groovy konwersji args (które jest typu Object[] lub Object w zależności od przekazania) na listę, 
   aby skorzystać z metody .sum().

   // ... wewnątrz klasy DynamicDataContainer ...
```groovy
    def methodMissing(String name, args) {
    
    // Obsługa wyzwania: Dynamiczna suma
    if (name == "calculateSum") {
        // args to tablica argumentów przekazanych do metody (np. [1, 2, 3])
        // .sum() to metoda Groovy'ego na kolekcjach
        return args.toList().sum()
    }
    
    // Poprzednia logika get/set (nie usuwaj jej!)
    }
```


2. Test Spock `DynamicSpec.groovy`
   Dodaj ten test do swojej klasy DynamicSpec. 
   Sprawdzamy tu dwie rzeczy:
    - czy suma działa dla różnych zestawów liczb
    - czy methodMissing faktycznie obsługuje dowolną liczbę argumentów.

```groovy
def "powinien dynamicznie obliczyć sumę dowolnej liczby argumentów"() {
        given: "nasz dynamiczny kontener"
        def container = new DynamicDataContainer()

        expect: "kalkulator obsługuje dynamiczną liczbę argumentów"
        container.calculateSum(10, 20, 30) == 60
        container.calculateSum(5, 5) == 10
        container.calculateSum(100) == 100
        container.calculateSum() == 0 // Pusta suma (dla Groovy sum() na pustej liście to null, warto sprawdzić czy rzuca błąd czy zwraca 0)
    }
```

Dlaczego to jest "Magia Ekspercka"?

Rozszerzalność: 
Jeśli jutro szef powie: "Potrzebujemy metody calculateAvg(1, 2, 3)", nie musisz modyfikować DynamicDataContainer. 
Po prostu dodajesz kolejny if w methodMissing.

Runtime Power: 
To, że klasa nie ma zdefiniowanej metody calculateSum, nie ma znaczenia. 
Kompilator nie krzyczy, bo wie, że w Groovy istnieje mechanizm "ostatniej szansy" (czyli methodMissing).

To jest to, co robi Spring Data: 
Kiedy piszesz w Spring Data: 
`repository.findByLastNameAndAge(name, age)`, Spring używa dokładnie tego mechanizmu (przechwytuje nazwę metody, parsuje ją i tworzy zapytanie SQL).

