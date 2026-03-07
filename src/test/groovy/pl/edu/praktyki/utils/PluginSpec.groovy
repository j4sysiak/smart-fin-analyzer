package pl.edu.praktyki.utils

import spock.lang.Specification

class PluginSpec extends Specification {

    def "powinien uruchomić wtyczki z logiką użytkownika"() {
        given: "manager"
        def pm = new PluginManager()

        and: "wtyczki zdefiniowane w locie"
        pm.addPlugin { println "Logowanie: $it" }
        pm.addPlugin { if(it.size() > 5) println "Alert: Długi tekst!" }

        when:
        pm.runAll("Ala ma kota")

        then:
        noExceptionThrown()
    }
}