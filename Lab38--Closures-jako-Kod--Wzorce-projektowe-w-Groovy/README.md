Lab 38
------

przechodzimy do Poziomu Hardcore. 
Zostawiamy Springa w spokoju i uderzamy w niskopoziomową magię JVM, którą Groovy pozwala ujarzmić w elegancki sposób.

Oto plan na Fazę 5: Java Interop & Meta-Programming Advanced.

Lab 38: Closures jako "Kod" (Wzorce projektowe w Groovy)
--------------------------------------------------------

W Javie wzorzec `Strategy` to kilka klas i interfejsów. 
W Groovy to jeden `Closure`. 
Nauczysz się, jak budować elastyczne systemy wtyczkowe.

Zadanie: 
System wtyczek ("Plugin System")

Stwórz klasę `PluginManager` w pakiecie pl.edu.praktyki.utils, która nie wie, co mają robić wtyczki, dopóki użytkownik ich nie "wstrzyknie".

Kod (PluginManager.groovy):

```groovy
package pl.edu.praktyki.utils

class PluginManager {
    // Lista domknięć (pluginów)
    private List<Closure> plugins = []

    void addPlugin(Closure plugin) {
        plugins << plugin
    }

    void runAll(Object data) {
        plugins.each { it(data) }
    }
}
```

Test (PluginSpec.groovy): w pakiecie: pl.edu.praktyki.utils

```groovy
package pl.edu.praktyki.utils
import spock.lang.Specification

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
```











