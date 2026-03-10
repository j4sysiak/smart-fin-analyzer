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
        recordedLength == 29 // "Java 25 wprowadzona na rynek!".length()
    }

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
}