Lab 44
------

Wracamy do czystej inżynierii oprogramowania i pisania pięknego kodu.
Zajmiemy się jednym z najważniejszych wzorców w aplikacjach biznesowych: 
  Łańcuchem Zobowiązań (Chain of Responsibility), połączonym z architekturą typu Pipeline (Rurociąg).

Wykorzystamy to do zbudowania Systemu Wykrywania Oszustw (Fraud Detection) dla naszych transakcji.

Dlaczego ten wzorzec? (Problem)

Junior programista waliduje transakcję tak:

```groovy
if (tx.amount > 10000) { return "Odrzucono: Za duża kwota" }
else if (tx.country == "KrajObok") { return "Odrzucono: Podejrzana lokalizacja" }
else if (tx.date.isAfter(midnight)) { return "Odrzucono: Przerwa bankowa" }
// ... i tak 50 linijek if-else ...
```

Taki kod to koszmar w utrzymaniu. 
Trudno to testować, a dodanie nowej reguły powoduje puchnięcie pliku w nieskończoność.
Rozwiążemy to wzorcem Łańcucha Zobowiązań.

Lab 33: Wzorzec "Chain of Responsibility" (Fraud Detection)
-----------------------------------------------------------

Cel: 
Rozbicie wielkiego if-else na małe, niezależne i łatwe do testowania klasy (ogniwa łańcucha), które będą po kolei oceniać transakcję.

Krok 1: Definicja "Ogniwa" łańcucha (Interfejs)
-----------------------------------------------

Stwórz plik `src/main/groovy/pl/edu/praktyki/fraud/FraudRule.groovy`:

```groovy
package pl.edu.praktyki.fraud

import pl.edu.praktyki.domain.Transaction

interface FraudRule {
/**
* Zwraca treść ostrzeżenia, jeśli transakcja jest oszustwem.
* Zwraca 'null', jeśli transakcja jest bezpieczna.
*/
String check(Transaction tx)
}
```

Krok 2: Tworzenie konkretnych reguł (Wdrożenie OOP)
---------------------------------------------------

Każda reguła żyje we własnym pliku. 
Robi tylko jedną rzecz i robi ją dobrze (Zasada Single Responsibility).

Reguła 1: `src/main/groovy/pl/edu/praktyki/fraud/AmountFraudRule.groovy`

```groovy
package pl.edu.praktyki.fraud

import pl.edu.praktyki.domain.Transaction

class AmountFraudRule implements FraudRule {
    
@Override
String check(Transaction tx) {
     if (tx.amountPLN && tx.amountPLN < -15000) {
       return "FRAUD: Podejrzanie wysoka kwota operacji (${tx.amountPLN} PLN)"
     }
     return null // Wszystko OK
     }
}
```


Reguła 2: `src/main/groovy/pl/edu/praktyki/fraud/NightTimeFraudRule.groovy`

```groovy
package pl.edu.praktyki.fraud
import pl.edu.praktyki.domain.Transaction

class AmountFraudRule implements FraudRule {
    @Override
    String check(Transaction tx) {
        if (tx.amountPLN && tx.amountPLN < -15000) {
            return "FRAUD: Podejrzanie wysoka kwota operacji (${tx.amountPLN} PLN)"
        }
        return null // Wszystko OK
    }
}
```
 
Krok 3: Zarządca Łańcucha (Processor / Pipeline)
------------------------------------------------

To jest serce wzorca. 
Klasa, która zbiera wszystkie reguły i przepuszcza przez nie transakcję jedna po drugiej.

Stwórz `src/main/groovy/pl/edu/praktyki/fraud/FraudDetector.groovy`:

W tym pliku używamy metody findResult. To czysta magia Groovy'ego – przelatuje ona przez listę reguł,
a gdy tylko pierwsza z nich zwróci coś innego niż null (czyli znajdzie Fraud),
od razu przerywa pętlę i zwraca ten komunikat.

```groovy
package pl.edu.praktyki.fraud

import pl.edu.praktyki.domain.Transaction

class FraudDetector {

    // Lista naszych reguł (To jest nasz 'Łańcuch Zobowiązań')
    private final List<FraudRule> rules =[
        new AmountFraudRule(),
        new NightTimeFraudRule()
    ]

    // Pozwala na dodawanie nowych reguł w locie (np. w testach)
    void addRule(FraudRule rule) {
        rules << rule
    }

    /**
     * Przepuszcza transakcję przez łańcuch.
     * Zwraca komunikat błędu (pierwszy napotkany) lub null, jeśli transakcja jest OK.
     */
    String detectFraud(Transaction tx) {
        
        // Groovy Magic: findResult iteruje po regułach. 
        // Jeśli rule.check(tx) zwróci Stringa (nie null), findResult natychmiast
        // kończy pętlę i zwraca ten tekst. Jeśli przejdzie wszystko i wszędzie będzie null,
        // zwróci null (czyli brak Fraudu).
        return rules.findResult { rule -> 
            rule.check(tx) 
        }
    }
}
```

Krok 4: Testowanie Łańcucha (Test Spock)
----------------------------------------

Sprawdźmy, czy łańcuch prawidłowo łapie oszustwa. 
Zauważ, że testujemy całą klasę `FraudDetector`, a ona pod spodem wywołuje poszczególne reguły z listy.

Stwórz `src/test/groovy/pl/edu/praktyki/fraud/FraudDetectorSpec.groovy`:

```groovy
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
```

Dlaczego ten wzorzec (Chain of Responsibility) to Czysta Inżynieria?

Zasada Otwarty-Zamknięty (Open-Closed Principle):
Jeśli biznes przyjdzie jutro i powie: "Chcemy blokować transakcje z Rosji", Ty nie dotykasz starego kodu (FraudDetector ani AmountFraudRule). 
Po prostu tworzysz nowy plik `CountryFraudRule.groovy` i dopisujesz go do listy. 
Zero ryzyka zepsucia starych rzeczy!

Niezależne Testowanie:
Każdą regułkę z osobna możesz przetestować szybkim testem jednostkowym (np. przetestować sam NightTimeFraudRule), 
bez włączania reszty łańcucha. W drabince if-else musiałbyś zawsze przechodzić przez wszystkie poprzednie if-y.

Groovy findResult:
W Javie musiałbyś napisać pętlę for, wewnątrz if (result != null) return result;. 
W Groovy jedna metoda na kolekcji załatwia całą sprawę wczesnego wyjścia (early exit).