Lab 45
------

Wzorzec, który w Javie bywa koszmarnie nudny do napisania, 
a w Groovym – dzięki magii AST – pisze się go w 3 linijki.

Wchodzimy w Lab 34: Wzorzec Dekorator (Decorator) na sterydach.
---------------------------------------------------------------

Problem z klasycznym Dekoratorem w Javie

Wzorzec Dekorator polega na "owijaniu" jednego obiektu w drugi (jak matrioszki), aby dodawać nowe funkcje (np. dodanie formatowania HTML do zwykłego tekstu).
Problem w Javie? Jeśli interfejs ma 10 metod, a Ty chcesz zmienić (udekorować) tylko jedną, i tak musisz zaimplementować pozostałe 9, pisząc w nich tylko return inner.method(). To straszny boilerplate.

Groovy załatwia to jedną adnotacją: `@Delegate`.

Cel:
Stworzenie systemu do eksportu transakcji. 
Będziemy mieli podstawowy eksporter (zwykły tekst), a następnie nałożymy na niego dekoratory: HTML oraz "Confidential" (ukrywający kwoty).

Krok 1: Interfejs i Podstawowa Implementacja
--------------------------------------------

Stwórz plik `src/main/groovy/pl/edu/praktyki/export/TransactionExporter.groovy`:

```groovy
package pl.edu.praktyki.export
import pl.edu.praktyki.domain.Transaction

interface TransactionExporter {
    String exportRow(Transaction tx)
    String exportHeader()
}
```
 

implementacja interfejsu:
Stwórz plik `src/main/groovy/pl/edu/praktyki/export/BasicExporter.groovy`:

```groovy
package pl.edu.praktyki.export

import pl.edu.praktyki.domain.Transaction

class BasicExporter implements TransactionExporter {

    @Override
    String exportRow(Transaction tx) {
        return "TX: ${tx.id} | KWOTA: ${tx.amountPLN}"
    }

    @Override
    String exportHeader() {
        return "--- LISTA TRANSAKCJI ---"
    }
}
```
 
Krok 2: Tworzenie Dekoratorów (Groovy Magic)
--------------------------------------------

Teraz tworzymy dekoratory. 
Zwróć uwagę na `@Delegate`. 
Mówi on kompilatorowi: 
"Jeśli ktoś wywoła na mnie metodę z interfejsu, której nie nadpisałem, 
     to po prostu przekaż to wywołanie do obiektu inner".


DEKORATOR 1:
------------
Stwórz plik `src/main/groovy/pl/edu/praktyki/export/HtmlExporterDecorator.groovy`:

```groovy
package pl.edu.praktyki.export

import pl.edu.praktyki.domain.Transaction
import groovy.transform.Delegate

// DEKORATOR 1: Formatuje wszystko jako HTML
class HtmlExporterDecorator implements TransactionExporter {

    // Magia: Groovy sam wygeneruje delegacje dla wszystkich metod z TransactionExporter!
    @Delegate 
    private final TransactionExporter inner

    HtmlExporterDecorator(TransactionExporter inner) {
        this.inner = inner
    }

    // Nadpisujemy TYLKO tę metodę, którą chcemy zmienić (udekorować)
    @Override
    String exportRow(Transaction tx) {
        return "<tr><td>${inner.exportRow(tx)}</td></tr>"
    }
    
    @Override
    String exportHeader() {
        return "<h1>${inner.exportHeader()}</h1>"
    }
}
```

DEKORATOR 2:
------------
Stwórz plik `src/main/groovy/pl/edu/praktyki/export/ConfidentialExporterDecorator.groovy`:

```groovy
package pl.edu.praktyki.export

import pl.edu.praktyki.domain.Transaction
import groovy.transform.Delegate

// DEKORATOR 2: Cenzuruje kwoty (Confidential)
class ConfidentialExporterDecorator implements TransactionExporter {
    
    @Delegate 
    private final TransactionExporter inner

    ConfidentialExporterDecorator(TransactionExporter inner) {
        this.inner = inner
    }

    // Nadpisujemy TYLKO exportRow. 
    // exportHeader() zostanie automatycznie wywołane z obiektu 'inner' dzięki @Delegate!
    @Override
    String exportRow(Transaction tx) {
        String raw = inner.exportRow(tx)
        // Podmieniamy liczby na gwiazdki używając regexa
        return raw.replaceAll(/[0-9]+(\.[0-9]+)?/, "***.**")
    }
}
```

Krok 3: Test Spock (Składanie Matrioszki)
-----------------------------------------

W teście zobaczymy, jak możemy dowolnie łączyć te funkcjonalności w czasie działania programu.

Stwórz plik `src/test/groovy/pl/edu/praktyki/export/DecoratorSpec.groovy`:

```groovy
package pl.edu.praktyki.export

import spock.lang.Specification
import pl.edu.praktyki.domain.Transaction

class DecoratorSpec extends Specification {

    def tx = new Transaction(id: "T-123", amountPLN: 450.50)

    def "powinien użyć podstawowego eksportera"() {
        given:
        def exporter = new BasicExporter()

        expect:
        exporter.exportHeader() == "--- LISTA TRANSAKCJI ---"
        exporter.exportRow(tx) == "TX: T-123 | KWOTA: 450.5"
    }

    def "powinien udekorować eksport tagami HTML"() {
        given: "owijamy BasicExporter w HtmlDecorator"
        def exporter = new HtmlExporterDecorator(new BasicExporter())

        expect:
        exporter.exportHeader() == "<h1>--- LISTA TRANSAKCJI ---</h1>"
        exporter.exportRow(tx) == "<tr><td>TX: T-123 | KWOTA: 450.5</td></tr>"
    }

    def "powinien użyć obu dekoratorów naraz (HTML + Cenzura)"() {
        given: "podwójna matrioszka: Html(Confidential(Basic))"
        def exporter = new HtmlExporterDecorator(
            new ConfidentialExporterDecorator(
                new BasicExporter()
            )
        )

        when:
        def result = exporter.exportRow(tx)

        then: "dane są zacenzurowane ORAZ owinięte w HTML"
        result == "<tr><td>TX: T-***.** | KWOTA: ***.**</td></tr>"
        
        and: "header działa, mimo że Confidential go nie nadpisał (Magia @Delegate)"
        exporter.exportHeader() == "<h1>--- LISTA TRANSAKCJI ---</h1>"
    }
}
```

Dlaczego to jest potężne (Architektura Mid/Senior)?

Zasada Otwarty-Zamknięty (OCP): 
Masz nowy wymóg biznesowy? 
"Chcemy, żeby eksport był szyfrowany Base64". 
Piszesz nowa klasę: `Base64Decorator`. 
Nie dotykasz ani jednej linijki w `BasicExporter` ani `HtmlDecorator`.

Kompozycja w Runtime: 
W przeciwieństwie do dziedziczenia `class HtmlConfidentialExporter extends BasicExporter`, 
                         gdzie kombinacji jest nieskończenie wiele, tutaj łączysz funkcje w locie.

Moc `@Delegate`: 
W klasie `ConfidentialExporterDecorator` w ogóle nie napisaliśmy metody exportHeader(). 
A jednak test przeszedł! 
To dlatego, że adnotacja `@Delegate` w tle dodała tę metodę i skierowała ją prosto do ukrytego wewnątrz obiektu inner.
Zobaczysz, jak w piękny sposób można "opakowywać" zachowania obiektów.