Lab 59
------

Lab59--Wzorzec-Singleton--oraz-Spring-Scopes
--------------------------------------------

Ten wzorzec jest "koniem roboczym" każdego systemu Spring Boot. 
Jeśli go opanujesz, zrozumiesz, jak Spring zarządza pamięcią i dlaczego Twój serwis działa tak szybko.

Dlaczego to jest ważne?
-----------------------

W Javie/Groovy `Wzorzec Singleton` (jedna instancja obiektu w całym systemie) jest często źle rozumiany. 
W Springu prawie wszystko jest `Singletonem` domyślnie, ale... czy wiesz, co się dzieje, 
gdy dwa wątki ( z wywolania wielowątkowości: GParsPool.withPool() ) próbują zmienić ten sam stan w Singletonie?

Singleton i Problem Stanu (Stateful vs Stateless)
-------------------------------------------------

Cel: Zrozumieć, dlaczego serwisy w Springu MUSZĄ być bezstanowe (Stateless).

Krok 1: Niebezpieczny Singleton (To, czego NIE robić)
-----------------------------------------------------

Stwórz `src/main/groovy/pl/edu/praktyki/singleton/BadService.groovy`:

```groovy
package pl.edu.praktyki.singleton

import org.springframework.stereotype.Service

@Service
class BadService {
// NIEBEZPIECZEŃSTWO! To pole współdzielą WSZYSCY użytkownicy
private int counter = 0

    void increment() {
        counter++ 
    }
    
    int getCounter() { return counter }
}
```

Krok 2: Test "Paranoi Wielowątkowej" (Spock)
--------------------------------------------

Sprawdźmy, czy ten "Singleton" jest bezpieczny.

```groovy
package pl.edu.praktyki.singleton

import spock.lang.Specification
import groovyx.gpars.GParsPool

class SingletonSpec extends Specification {

    def "powinien pokazać wyścig danych (Race Condition)"() {
        given:
        def service = new BadService()

        when: "uruchamiamy 100 wątków, które inkrementują licznik"
        GParsPool.withPool {
            (1..100).collectParallel { 
                service.increment() 
            }
        }

        then: "licznik powinien wynosić 100"
        // W 99% przypadków tutaj wybuchnie błąd, bo wątki nadpisują się nawzajem!
        service.counter == 100 
    }
}
```

Czego uczą nas te wzorce?

Singleton w Springu: 
Spring tworzy tylko jedną instancję Twojego serwisu. 
Jeśli serwis ma zmienną private int counter, to jest ona wspólna dla wszystkich zapytań `REST` w całym systemie! 
To najczęstsza przyczyna "dziwnych błędów", których nie da się powtórzyć na lokalnej maszynie, a które wybuchają pod obciążeniem.

Stateless Services: 
To złota zasada architektury: Twoje serwisy powinny być bezstanowe. 
Jeśli musisz trzymać jakiś stan (jak counter), używaj:
 - `AtomicInteger`
 - `ConcurrentHashMap` 
 - albo (najlepiej) `bazy danych`

Wnioski: 
Po tym Labie będziesz rozumiał, że "Singleton" to nie tylko wzorzec, to odpowiedzialność za wielowątkowość.

Podsumowanie "Wzorcowe" (To do Readme.md)

Możesz dodać tę sekcję do swojego README.md, jako wisienkę na torcie:


## 🧩 Architektura i Wzorce Projektowe
System został zaprojektowany z wykorzystaniem najlepszych praktyk inżynierskich:
* **Chain of Responsibility:** Logika walidacji transakcji (Fraud Detection) rozbita na niezależne jednostki.
* **Strategy Pattern:** Dynamiczne wybieranie logiki procesowania (`VipOrderStrategy`, `StandardOrderStrategy`).
* **Proxy / AOP:** Przechwytywanie wywołań metod dla celów logowania czasu i bezpieczeństwa.
* **Facade:** Uproszczony interfejs (`SmartFinFacade`) ukrywający złożoność całego systemu.
* **Composite:** Rekurencyjne drzewa danych (Portfele inwestycyjne) z zachowaniem zasady Liskov.
* **Singleton (Spring Scope):** Świadome zarządzanie stanem aplikacji w środowisku wielowątkowym.
  Czy to wyczerpuje temat?

TAK. Jeśli rozumiesz te wzorce i potrafisz je zaimplementować w Groovym, to jesteś na poziomie,
który pozwala Ci wejść do dowolnego projektu opartego na JVM i szybko połapać się w jego architekturze.

 