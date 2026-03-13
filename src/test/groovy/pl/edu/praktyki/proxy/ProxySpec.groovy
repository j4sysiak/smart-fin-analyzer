package pl.edu.praktyki.proxy

import spock.lang.Specification

class ProxySpec extends Specification {

    def "powinien ukryć logikę dekorowania za interfejsem"() {
        given: "prawdziwy serwis (Dyrektor)"
        WorkService realDyrektor = new RealWorkService()

        and: "Proxy (Asystent) owijający Dyrektora"
        WorkService proxyInstance = new WorkServiceProxy(realDyrektor)

        when: "Klient (test) wywołuje metodę na interfejsie"
        // Klient nie wie, że rozmawia z asystentem!
        proxyInstance.doHeavyWork()

        then: "dla klienta obiekt wciąż implementuje interfejs i wynik jest zgodny z oczekiwaniami"
        proxyInstance instanceof WorkService
        noExceptionThrown()
    }

    def "demonstracja przezroczystości (Polimorfizm)"() {
        given: "listę różnych implementacji tego samego interfejsu"
        List<WorkService> pracownicy = [
                new RealWorkService(),
                new WorkServiceProxy(new RealWorkService())
        ]

        when: "wywołujemy tę samą metodę na każdym"
        pracownicy.each { pracownik ->
            println ">>> Wołam pracownika: ${pracownik.getClass().simpleName}"
            pracownik.doHeavyWork()
        }

        then: "wszyscy 'załatwili' sprawę"
        pracownicy.size() == 2
        noExceptionThrown()
    }
}