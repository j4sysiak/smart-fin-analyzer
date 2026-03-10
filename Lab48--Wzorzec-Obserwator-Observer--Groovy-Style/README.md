Lab 48
------

Lab 48: Wzorzec Obserwator (Observer) – Wersja "Groovy Style"
-------------------------------------------------------------

Problem: 
W Javie Wzorzec Obserwatora wymaga stworzenia interfejsu Observer, metody update(), trzymania listy obserwatorów w klasie i iterowania po nich. 
Dużo kodu.

Rozwiązanie w Groovy: 
Użyjemy domknięć (Closures) jako "Listenerów". 
Nasz system powiadomi wszystkie nasłuchujące funkcje bez konieczności definiowania jakichkolwiek interfejsów!

Krok 1: Klasa obserwowana (Subject)
-----------------------------------

Stwórz `src/main/groovy/pl/edu/praktyki/observer/NewsAgency.groovy`:

```groovy
package pl.edu.praktyki.observer

class NewsAgency {
// Lista naszych obserwatorów - przyjmujemy dowolne bloki kodu (Closures)
private List<Closure> listeners =[]

    // Rejestracja obserwatora (Subskrypcja)
    void subscribe(Closure listener) {
        listeners << listener
    }

    // Wywołanie zmiany stanu
    void publishNews(String breakingNews) {
        println ">>>[AGENCJA PRASOWA] Publikuję: $breakingNews"
        
        // Powiadamiamy wszystkich obserwatorów (odpalamy ich kod)
        listeners.each { it.call(breakingNews) }
    }
}
```

Krok 2: Test Spock (Obserwatorzy w akcji)
-----------------------------------------

Tutaj zasymulujemy kilka różnych reakcji na jeden "News".

Stwórz `src/test/groovy/pl/edu/praktyki/observer/ObserverSpec.groovy`:

```groovy
package pl.edu.praktyki.observer

import spock.lang.Specification

class ObserverSpec extends Specification {

    def "powinien powiadomić wszystkich subskrybentów za pomocą domknięć"() {
        given: "agencja prasowa"
        def agency = new NewsAgency()

        and: "zmienne do sprawdzenia efektów ubocznych w teście"
        boolean isTvAlerted = false
        int recordedLength = 0

        and: "subskrybent 1 - Telewizja (ustawia flagę)"
        agency.subscribe { String news -> 
            println "📺[TV] Przerywamy program! Mamy news: $news"
            isTvAlerted = true
        }

        and: "subskrybent 2 - System Analityczny (zbiera długość znaków)"
        agency.subscribe { String news -> 
            println "📈 [ANALITYKA] Zapisano długość wiadomości: ${news.length()}"
            recordedLength = news.length()
        }

        when: "publikujemy nowy artykuł"
        agency.publishNews("Java 25 wprowadzona na rynek!")

        then: "obie akcje nasłuchujące zostały wywołane automatycznie!"
        isTvAlerted == true
        recordedLength == 30 // "Java 25 wprowadzona na rynek!".length()
    }
}
```

Dlaczego to jest lepsze?

Niski próg wejścia (Low Coupling): 
Telewizja i System Analityczny nie muszą dziedziczyć po żadnej klasie bazowej ani implementować interfejsów (jak w standardowym GoF Design Patterns).

Reaktywność: 
Budujesz architekturę sterowaną zdarzeniami (Event-Driven) całkowicie natywnymi funkcjami języka. 
To dokładnie tak (pod spodem) działa Node.js czy nowoczesne frameworki UI (jak React).

Zadanie dla Ciebie: 
Zaimplementuj ten wzorzec i zobacz na konsoli piękny przepływ danych. 

Wyzwanie: 
dodaj metodę unsubscribe(Closure c), która pozwoli "wypisać się" obserwatorowi 
(wskazówka: musisz przypisać domknięcie do zmiennej w teście przed przekazaniem do subskrypcji, by mieć jego referencję do usunięcia!).


Rozwiązanie:
------------

Oto gotowe rozwiązanie wyzwania z Wzorcem Obserwatora.

To zadanie kryje w sobie bardzo ważną lekcję na temat tego, jak maszyna JVM (i sam Groovy) zarządza pamięcią i referencjami do obiektów.

1. Rozbudowa klasy NewsAgency.groovy
------------------------------------

Dodajemy metodę `unsubscribe`, która po prostu usuwa obiekt (domknięcie) z naszej wewnętrznej listy.

```groovy
package pl.edu.praktyki.observer

class NewsAgency {
private List<Closure> listeners =[]

    void subscribe(Closure listener) {
        listeners << listener
    }

    // NOWA METODA: Wypisywanie się
    void unsubscribe(Closure listener) {
        listeners.remove(listener)
    }

    void publishNews(String breakingNews) {
        println ">>>[AGENCJA PRASOWA] Publikuję: $breakingNews"
        listeners.each { it.call(breakingNews) }
    }
}
```

2. Test Spock: ObserverSpec.groovy
----------------------------------

Aby przetestować to działanie, napiszemy nowy scenariusz, w którym nasze "Radio" nasłuchuje pierwszej wiadomości, 
a potem rezygnuje z subskrypcji i przy drugiej wiadomości już milczy.

Dodaj to do klasy ObserverSpec:
------------------------------

```groovy
def "powinien pozwolić na wypisanie się z subskrypcji (unsubscribe)"() {
given: "agencja prasowa i zmienna stanu"
def agency = new NewsAgency()
int notificationCount = 0

        and: "KLUCZOWE: przypisujemy Closure do ZMIENNEJ, aby zapamiętać jego adres w pamięci"
        def radioListener = { String news ->
            println "📻 [RADIO] Odebrano: $news"
            notificationCount++
        }

        when: "Radio subskrybuje i Agencja wysyła pierwszą wiadomość"
        agency.subscribe(radioListener)
        agency.publishNews("Wiadomość 1 - Wszyscy słuchają!")

        then: "Radio otrzymało wiadomość"
        notificationCount == 1

        when: "Radio REZYGNUJE z subskrypcji, a Agencja wysyła drugą wiadomość"
        agency.unsubscribe(radioListener) // Używamy dokładnie tej samej referencji!
        agency.publishNews("Wiadomość 2 - Tylko dla aktywnych!")

        then: "Radio NIE OTRZYMAŁO drugiej wiadomości (licznik stoi w miejscu)"
        notificationCount == 1
    }
```

Czego nas uczy to Wyzwanie? (Pułapka dla Juniora)

Dlaczego prosiłem Cię w podpowiedzi o zapisanie domknięcia do zmiennej (def radioListener = { ... })?

Wyobraź sobie, że Junior napisałby to tak:

```groovy
// 1. Zapisuje się
agency.subscribe { println "Halo!" }

// 2. Próbuje się wypisać
agency.unsubscribe { println "Halo!" }
```


To by NIE ZADZIAŁAŁO! Dlaczego?
Za każdym razem, gdy używasz w kodzie klamerek { ... }, tworzysz nowy obiekt klasy Closure w pamięci RAM. 
Metoda unsubscribe szuka na liście obiektu za pomocą metody equals() / referencji.

Jeśli napiszesz { println "Halo!" } dwa razy, to są to dwa zupełnie inne obiekty dla procesora. 
Agencja nie usunęłaby pierwszego obserwatora, bo przekazałeś jej adres drugiego.

Przypisując zachowanie do zmiennej radioListener, gwarantujesz, że w subscribe() i unsubscribe() 
operujesz na dokładnie tym samym obiekcie. 
To fundament bezpiecznego zarządzania pamięcią i wzorcami projektowymi!














