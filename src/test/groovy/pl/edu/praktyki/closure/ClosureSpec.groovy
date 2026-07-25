package pl.edu.praktyki.closure

import spock.lang.Specification

class ClosureSpec extends Specification {

    def service = new ClosureSpecService()

    def "powinien zwrócić imię w przywitaniu"() {
        when: "wywołujemy metodę produkcyjną z argumentem"
        def result = service.greet("Ania")

        then: "dostajemy poprawny tekst"
        result == "Cześć, Ania"
    }

    def "powinien zwrocic prosty tekst bez parametrow"() {
        when: "uruchamiamy metodę bez argumentów"
        def result = service.hello()

        then: "metoda zwraca oczekiwany napis"
        result == "Hej!"
    }

    def "powinien dodac dwie liczby"() {
        when: "podajemy dwie liczby"
        def result = service.add(2, 3)

        then: "metoda zwraca ich sumę"
        result == 5
    }

    def "powinien przejść po wszystkich elementach listy"() {
        given: "mamy listę imion"
        def names = ["Ala", "Ola", "Jan"]

        when: "wywołujemy metodę produkcyjną"
        def greetings = service.greetAll(names)

        then: "zapisujemy wszystkie przywitania w tej samej kolejności"
        greetings == ["Witaj Ala", "Witaj Ola", "Witaj Jan"]
    }

    def "powinien uzyc zmiennej zewnetrznej w closure"() {
        when: "wywołujemy metodę z prefiksem"
        def result = service.greetWithPrefix("Pani", "Kasia")

        then: "metoda korzysta z przekazanego prefiksu"
        result == "Pani Kasia"
    }

    def "powinien zwrócić closure z innej closure"() {
        // To jest test Spocka sprawdzający, czy metoda multiplier(2) zwraca closure.
        when: "tworzymy closure do podwajania"
        // service.multiplier(2) tworzy nową funkcję z „zapamiętanym” mnożnikiem 2, czyli tworzy closure
        // to Closure jest zapisane do zmiennej timesTwo.

        def timesTwo = service.multiplier(2)

        // def timesTwoClosure =  { int value -> value * factor }
           def timesTwoClosure =  { int value -> value * 2 }

        then: "nowa closure działa jak zwykła funkcja"
        // timesTwo(5) wywołuje tę zwróconą closure z argumentem 5.
        // Oczekiwany wynik to 10, więc test potwierdza, że działa jak funkcja mnożąca przez 2.
        timesTwo(5) == 10
        timesTwoClosure(5) == 10
    }
}