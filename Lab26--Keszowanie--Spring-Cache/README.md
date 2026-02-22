Lab 26 - Keszowanie - Spring Cache
----------------------------------
to jest kontynuacja Lab 25 - z projektu: `c:\dev\proj-groovy`
 
Dodajemy pamięć podręczną, aby aplikacja nie uderzała do API internetowego przy każdej z tysięcy transakcji.

Krok 1: Włącz keszowanie w głównych aplikacjach
-----------------------------------------------

Dodaj adnotację @EnableCaching do swoich klas startowych.
W pliku `src/main/groovy/pl/edu/praktyki/SmartFinDbApp.groovy` dodaj to nad klasą:

```groovy
import org.springframework.cache.annotation.EnableCaching // <-- Dodaj import

@SpringBootApplication
@EnableCaching // <-- DODAJ TO
class SmartFinDbApp implements CommandLineRunner {
// ... reszta bez zmian
```

Krok 2: Dodaj adnotację @Cacheable do metody pobierającej kursy walut
--------------------------------------------------------------

W pliku `src/main/groovy/pl/edu/praktyki/SmartFinApp.groovy` (jeśli go używasz):

```groovy
import org.springframework.cache.annotation.EnableCaching

@Configuration
@ComponentScan(basePackages = "pl.edu.praktyki.service")
@EnableCaching // <-- DODAJ TO
class SmartFinConfig {}
```

Krok 3: Powiedz serwisowi, co ma zapamiętywać
---------------------------------------------


Otwórz plik:
  `src/main/groovy/pl/edu/praktyki/service/CurrencyService.groovy` i dodaj jedną adnotację nad metodą getExchangeRate:


```groovy
package pl.edu.praktyki.service

import org.springframework.stereotype.Service
import org.springframework.cache.annotation.Cacheable // <-- Import
import groovy.json.JsonSlurper
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

@Service
class CurrencyService {
// ... zmienne ...

    // DODAJ TĘ ADNOTACJĘ:
    @Cacheable("exchangeRates")
    BigDecimal getExchangeRate(String fromCurrency) {
        if (fromCurrency == "PLN") return 1.0
        
        println ">>> [API CALL] Pobieram kurs z internetu dla: $fromCurrency" // <-- Dodaj ten print
        
        // ... reszta Twojego kodu bez żadnych zmian ...
```

Dlaczego to nie zepsuje testów?
Test w Spocku wywoła tę metodę tak samo jak wcześniej. 
Spock nie dba o to, skąd serwis wziął wynik (z sieci czy z pamięci RAM). 
Wynik będzie taki sam (np. 4.32), więc testy będą zielone!


