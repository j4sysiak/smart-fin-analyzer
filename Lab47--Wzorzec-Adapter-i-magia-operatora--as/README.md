Lab 47
------

Teraz uderzamy w kolejny klasyk. 
Tym razem pokażę Ci, jak Groovy potrafi zniszczyć 50 linijek "javowego" boilerplate'u i zamienić go w 5 linijek czystej poezji.

Wchodzimy w Lab 47: Wzorzec Adapter (i magia operatora as).
-----------------------------------------------------------

Problem z klasycznym Adapterem w Javie

Wzorzec Adapter (tzw. Wrapper) jest używany, gdy masz swój piękny, czysty interfejs w systemie, 
ale musisz zintegrować się z brzydką, starą biblioteką (Legacy API), która ma zupełnie inne nazwy metod.
W Javie musisz stworzyć nową klasę `LegacyAdapter`, zaimplementować swój interfejs, 
wstrzyknąć stary obiekt i pisać nudne metody delegujące.

W Groovym zrobimy to w locie, używając tzw. `Map Coercion` (rzutowania mapy na interfejs).

Krok 1: Nasz system i Stare API
-------------------------------

Stwórzmy interfejs naszego nowoczesnego systemu finansowego oraz zasymulujmy stare, zewnętrzne API bankowe.

Stwórz plik `src/main/groovy/pl/edu/praktyki/adapter/PaymentSystem.groovy`:

```groovy
package pl.edu.praktyki.adapter

// 1. NASZ NOWOCZESNY INTERFEJS
interface PaymentProvider {
    boolean processPayment(String accountId, BigDecimal amount)
    String getStatus()
}

// 2. STARE, BRZYDKIE API BANKU (Nie możemy go modyfikować!)
class LegacyBankApi {
    // Inna nazwa metody, inne typy parametrów (double zamiast BigDecimal)
    boolean makeOldTransfer(String targetAccount, double cash) {
        println "[LEGACY BANK] Wysyłam ${cash} do ${targetAccount}..."
        return true
    }

    String checkBankHealth() {
        return "OPERATIONAL"
    }
}
```

Krok 2: Groovy Adapter w akcji (Test Spock)
-------------------------------------------

W Javie musielibyśmy napisać klasę `LegacyBankAdapter implements PaymentProvider`. 
Zobacz, jak elegancko rozwiązuje to Groovy za pomocą mapy i operatora as.

Stwórz plik `src/test/groovy/pl/edu/praktyki/adapter/AdapterSpec.groovy`:

```groovy
package pl.edu.praktyki.adapter

import spock.lang.Specification

class AdapterSpec extends Specification {

    def "powinien zaadaptować LegacyBankApi do PaymentProvider używając rzutowania Mapy"() {
        given: "obiekt starego API"
        def legacyApi = new LegacyBankApi()

        and: "tworzymy adapter W LOCIE używając mapy z domknięciami (Closures) i operatora 'as'"
        // Klucze w mapie odpowiadają nazwom metod z interfejsu!
        PaymentProvider adapter =[
            // Mapujemy metodę processPayment
            processPayment: { String id, BigDecimal amount -> 
                // Wywołujemy stare API, dokonując konwersji typów
                legacyApi.makeOldTransfer(id, amount.doubleValue()) 
            },
            
            // Mapujemy metodę getStatus
            getStatus: { -> 
                legacyApi.checkBankHealth() 
            }
        ] as PaymentProvider // <--- TA LINIJKA TO CZYSTA MAGIA GROOVY!

        when: "używamy adaptera jak normalnego obiektu z naszego systemu"
        boolean isSuccess = adapter.processPayment("PL-12345", 500.50)
        String status = adapter.getStatus()

        then: "adapter poprawnie komunikuje się ze starym API"
        isSuccess == true
        status == "OPERATIONAL"

        and: "obiekt faktycznie jest traktowany przez JVM jako instancja PaymentProvider"
        adapter instanceof PaymentProvider
    }
}
```
Dlaczego to jest technika "Senior Level"?

Map-to-Interface Coercion (as Interface):
Kiedy piszesz [...] as PaymentProvider, Groovy używa mechanizmu zwanego Proxy, aby w locie wygenerować klasę, 
która implementuje ten interfejs. 
Kiedy ktoś wywoła metodę interfejsu, Groovy zagląda do Twojej Mapy i szuka w niej klucza o takiej samej nazwie. 
Jeśli znajdzie Closure, po prostu go uruchamia.

Koniec z klasami-wydmuszkami:
W wielu projektach Javowych masz dziesiątki klas *Adapter, które tylko przepychają dane z metody do metody. 
W Groovy, jeśli adapter jest prosty, robisz to "w miejscu" konfigurowania modułu (np. w konfiguracji Springa).

Genialne do Mockowania:
Wiedziałeś, że w ten sposób możesz błyskawicznie tworzyć mocki/stuby bez używania frameworka (jak Mockito czy wbudowane Mocki Spocka)? 
Jeśli interfejs ma 1 metodę, możesz go zamockować jednym domknięciem!

Twoje zadanie / Wyzwanie:
-------------------------

Zaimplementuj ten kod i odpal test. 
Zobaczysz w konsoli [LEGACY BANK]..., co dowodzi, że mapa skutecznie przekierowała wywołanie.


Wyzwanie ("Partial Implementation"):
------------------------------------

Co się stanie, jeśli usuniesz z mapy implementację metody getStatus i zostawisz samo processPayment 
(a potem usuniesz wywołanie getStatus() z bloku when:)?
W Javie dostałbyś błąd kompilacji ("Must implement all abstract methods"). 
A co zrobi Groovy z użyciem as? Sprawdź to! To kolejna funkcja, za którą testerzy kochają ten język.

Rozwwiazanie:
-------------

I oto odkryłeś jedną z największych tajemnic i supermocy Groovy'ego! 🎉

Jeśli usunąłeś z mapy implementację getStatus, a w teście wywołałeś tylko processPayment, zauważyłeś coś, co w głowie Javowca po prostu się nie mieści: Kompilator na to pozwolił, a test przeszedł na zielono!

Dlaczego to jest absolutnie genialne? (Tzw. "Partial Implementation")

W Javie (lub używając anonimowych klas wewnętrznych), jeśli implementujesz interfejs, musisz zaimplementować wszystkie jego metody. Nieważne, czy ich użyjesz, czy nie.

W Groovym, używając operatora as, zachodzi mechanizm "Duck Typing" (Kaczego Typowania) połączonego z dynamicznym Proxy:

Groovy tworzy obiekt implementujący Twój interfejs w ułamku sekundy.

Zgadza się na brak niektórych metod.

Dopóki Twój kod fizycznie nie wywoła brakującej metody w trakcie działania programu – wszystko działa idealnie.

A co by się stało, gdybyś jednak wywołał to usunięte getStatus()? Groovy rzuciłby bardzo czytelny błąd:
java.lang.UnsupportedOperationException.

Dlaczego Testerzy to kochają? (Prawdziwy przypadek z produkcji)

Wyobraź sobie, że musisz przetestować metodę, która na wejściu przyjmuje wielki obiekt systemowy, np. java.sql.Connection (interfejs z Javy, który ma ponad 50 metod!).
Twój kod używa tylko jednej z nich: prepareStatement().

W Javie: Musisz użyć ciężkiego frameworka (jak Mockito) albo napisać klasę-wydmuszkę (Stub), która ma 50 metod, z czego 49 zwraca null. Koszmar.
W Groovym: Robisz to w jednej linijce:

```groovy
def connectionMock =[
prepareStatement: { String sql -> return myFakeStatement }
] as java.sql.Connection
```


To jest tzw. "Błyskawiczne Stubowanie". 
Piszesz tylko to, co faktycznie będzie testowane. 
Jeśli Twój test nagle wywali UnsupportedOperationException, to jest dla Ciebie sygnał ostrzegawczy: 
"Uwaga! Ktoś dopisał do logiki biznesowej wywołanie nowej metody z bazy danych, muszę zaktualizować test!".
