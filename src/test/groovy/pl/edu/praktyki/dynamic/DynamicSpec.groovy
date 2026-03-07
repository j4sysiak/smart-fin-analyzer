package pl.edu.praktyki.dynamic

import spock.lang.Specification

class DynamicSpec extends Specification {

    def "powinien dynamicznie obsłużyć metody get/set"() {
        given: "nasz dynamiczny kontener"
        def container = new DynamicDataContainer()

        when: "wywołujemy metody, których nie ma w kodzie klasy np setName  lub getName"
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

    def "powinien dynamicznie obliczyć sumę dowolnej liczby argumentów"() {
        given: "nasz dynamiczny kontener"
        def container = new DynamicDataContainer()

        expect: "kalkulator obsługuje dynamiczną liczbę argumentów"
        container.calculateSum(10, 20, 30) == 60
        container.calculateSum(5, 5) == 10
        container.calculateSum(100) == 100
        container.calculateSum() == 0 // Pusta suma (dla Groovy sum() na pustej liście to null, warto sprawdzić czy rzuca błąd czy zwraca 0)
    }
}