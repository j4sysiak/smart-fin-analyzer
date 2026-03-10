package pl.edu.praktyki.adapter

import spock.lang.Specification

class AdapterSpec extends Specification {

    def "powinien zaadaptować LegacyBankApi do PaymentProvider używając rzutowania Mapy"() {
        given: "obiekt starego API"
        def legacyApi = new LegacyBankApi()

        and: "tworzymy adapter W LOCIE używając mapy z domknięciami (Closures) i operatora 'as'"
        // Klucze w mapie odpowiadają nazwom metod z interfejsu!
        PaymentProvider adapter = [
                // Mapujemy metodę processPayment
                processPayment: { String id, BigDecimal amount ->
                    // Wywołujemy stare API, dokonując konwersji typów
                    legacyApi.makeOldTransfer(id, amount.doubleValue())
                }

                // Mapujemy metodę getStatus
                //getStatus: { ->
                //    legacyApi.checkBankHealth()
                //}
        ] as PaymentProvider // <--- TA LINIJKA TO CZYSTA MAGIA GROOVY!

        when: "używamy adaptera jak normalnego obiektu z naszego systemu"
        boolean isSuccess = adapter.processPayment("PL-12345", 500.50)
        //String status = adapter.getStatus()

        then: "adapter poprawnie komunikuje się ze starym API"
        isSuccess == true
        //status == "OPERATIONAL"

        and: "obiekt faktycznie jest traktowany przez JVM jako instancja PaymentProvider"
        adapter instanceof PaymentProvider
    }
}