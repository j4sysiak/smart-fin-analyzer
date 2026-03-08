package pl.edu.praktyki.fraud

import spock.lang.Specification
import pl.edu.praktyki.domain.Transaction

class FraudDetectorSpec extends Specification {

    def detector = new FraudDetector()

    def "powinien zidentyfikować zwykłą transakcję jako bezpieczną"() {
        given:
        def tx = new Transaction(id: "T1", amountPLN: -500.0, description: "Zakupy spożywcze")

        expect: "zwraca null (brak alertu)"
        detector.detectFraud(tx) == null
    }

    def "powinien zablokować transakcję ze względu na zbyt wysoką kwotę"() {
        given:
        def tx = new Transaction(id: "T2", amountPLN: -20000.0, description: "Kupno samochodu")

        when:
        def result = detector.detectFraud(tx)

        then: "reaguje AmountFraudRule"
        result != null
        result.contains("Podejrzanie wysoka kwota")
    }

    def "powinien zablokować dużą nocną transakcję"() {
        given:
        // Kwota poniżej 15000 (więc AmountFraudRule to zignoruje!),
        // ale powyżej 5000 i w nocy - więc NightTimeFraudRule to złapie!
        def tx = new Transaction(id: "T3", amountPLN: -6000.0, description: "Przelew NIGHT club")

        when:
        def result = detector.detectFraud(tx)

        then:
        result != null
        result.contains("środku nocy")
    }
}