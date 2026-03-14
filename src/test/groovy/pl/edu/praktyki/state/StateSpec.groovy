package pl.edu.praktyki.state

import spock.lang.Specification

class StateSpec extends Specification {

    def "nie można przetworzyć transakcji przed jej weryfikacją"() {
        given: "zupełnie nowa transakcja (NewState)"
        def tx = new StatefulTransaction(id: "T1")

        when: "próbujemy ją przetworzyć z pominięciem weryfikacji"
        tx.process()

        then: "powinna zablokować akcję rzucając błąd z NewState"
        def ex = thrown(IllegalStateException)
        ex.message.contains("Najpierw zweryfikuj")
    }

    def "prawidłowy cykl (Happy Path) powinien zadziałać"() {
        given: "nowa transakcja"
        def tx = new StatefulTransaction(id: "T2")

        when: "weryfikujemy"
        tx.verify()

        then: "stan to VerifiedState"
        tx.currentState.class == VerifiedState

        when: "procesujemy (płacimy)"
        tx.process()

        then: "przechodzi bez błędów"
        noExceptionThrown()
    }

    def "anulowana transakcja staje się zablokowana"() {
        given:
        def tx = new StatefulTransaction(id: "T3")

        when: "anulujemy na samym starcie"
        tx.cancel()

        and: "próbujemy zweryfikować"
        tx.verify()

        then: "jest to zablokowane przez CancelledState"
        thrown(IllegalStateException)
    }
}