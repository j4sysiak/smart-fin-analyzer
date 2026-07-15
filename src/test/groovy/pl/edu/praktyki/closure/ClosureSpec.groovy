package pl.edu.praktyki.closure

import spock.lang.Specification

class ClosureSpec extends Specification {

    def "powinien zwrocic imie w przywitaniu"() {
        given: "tworzymy closure, ktora przyjmuje jedno imie"
        def greet = { name ->
            "Cześć, $name"
        }

        when: "wywolujemy closure z argumentem"
        def result = greet("Ania")

        then: "dostajemy poprawny tekst"
        result == "Cześć, Ania"
    }

    def "powinien zwrocic prosty tekst bez parametrow"() {
        given: "tworzymy closure bez argumentow"
        def hello = {
            "Hej!"
        }

        when: "uruchamiamy closure"
        def result = hello()

        then: "closure zwraca oczekiwany napis"
        result == "Hej!"
    }

    def "powinien dodac dwie liczby"() {
        given: "tworzymy closure z dwoma parametrami"
        def add = { a, b ->
            a + b
        }

        when: "podajemy dwie liczby"
        def result = add(2, 3)

        then: "closure zwraca ich sume"
        result == 5
    }

    def "powinien przejsc po wszystkich elementach listy"() {
        given: "mamy liste imion i pusta liste wynikow"
        def names = ["Ala", "Ola", "Jan"]
        def greetings = []

        when: "each wywoluje closure dla kazdego elementu"
        names.each { name ->
            greetings << "Witaj $name"
        }

        then: "zapisujemy wszystkie przywitania w tej samej kolejnosci"
        greetings == ["Witaj Ala", "Witaj Ola", "Witaj Jan"]
    }

    def "powinien uzyc zmiennej zewnetrznej w closure"() {
        given: "closure widzi zmienna z otaczajacego zakresu"
        def prefix = "Pani"
        def greet = { name ->
            "$prefix $name"
        }

        when: "uruchamiamy closure"
        def result = greet("Kasia")

        then: "closure korzysta z prefixu z zewnatrz"
        result == "Pani Kasia"
    }

    def "powinien zwrocic closure z innej closure"() {
        given: "closure fabrykujaca inne closure"
        def multiplier = { factor ->
            return { value ->
                value * factor
            }
        }

        when: "tworzymy closure do podwajania"
        def timesTwo = multiplier(2)

        then: "nowa closure dziala jak zwykla funkcja"
        timesTwo(5) == 10
    }
}