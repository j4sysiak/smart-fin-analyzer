Lab 56
------

Lab56--Wzorzec-Fabryka-i-Wzorzec-Null-Object-Pattern-------SOLID-w-praktyce
---------------------------------------------------------------------------

SOLID
-----

Musimy pisać kod, który jest odporny na zmiany. 
Do tego właśnie służą zasady `SOLID`.
Weźmiemy na warsztat dwie najważniejsze litery z tego skrótu:

 - `O - Open/Closed Principle` (Zasada Otwarty/Zamknięty): 
    Kod powinien być otwarty na rozszerzenia, ale zamknięty na modyfikacje.

 - `D - Dependency Inversion Principle` (Zasada Odwrócenia Zależności): 
    Moduły wysokiego poziomu nie powinny zależeć od modułów niskiego poziomu. 
    Oba powinny zależeć od abstrakcji (interfejsów).

Połączymy to z dwoma klasycznymi wzorcami: `Fabryka (Factory Method)` oraz `Pusty Obiekt (Null Object Pattern)`.

Problem w naszym projekcie:
---------------------------

Obecnie w `SmartFinCliRunner` mamy zahardkodowane dane:

```groovy
def rawData = [
new Transaction(id: "1", amount: 100, currency: "EUR", category: "Jedzenie", description: "Obiad", date: LocalDate.now()),
new Transaction(id: "2", amount: -50, currency: "USD", category: "Rozrywka", description: "Kino", date: LocalDate.now()),
new Transaction(id: "3", amount: 2000, currency: "PLN", category: "Praca", description: "Bonus", date: LocalDate.now())
]
```

W prawdziwym życiu użytkownik poda plik: `-f wyciag.csv` albo `-f wyciag.json`.
Junior napisałby gigantycznego ifa: 
Jeśli plik to CSV, użyj `CsvParsera`. 
Jeśli JSON, użyj `JsonParsera`. 

To łamie zasadę Open/Closed, bo przy dodaniu formatu `XML` musisz modyfikować ten brzydki kod z ifami.

Zrobimy to profesjonalnie.

Krok 1: Abstrakcja (Litera 'D' w SOLID)  - `D - Dependency Inversion Principle` (Zasada Odwrócenia Zależności)
--------------------------------------------------------------------------------------------------------------

Tworzymy interfejs. 
Nasz główny program będzie rozmawiał tylko z tym interfejsem, nie mając pojęcia, jak fizycznie czyta się `CSV` czy `JSON`, czy `XML`, czy `TXT`.

Stwórz plik` src/main/groovy/pl/edu/praktyki/parser/TransactionParser.groovy`:

```groovy
package pl.edu.praktyki.parser

import pl.edu.praktyki.domain.Transaction

interface TransactionParser {
    List<Transaction> parse(File file)
}
```

Krok 2: Konkretne Implementacje i Wzorzec "Null Object"
-------------------------------------------------------

Stwórzmy parser dla `CSV`. 
Co, jeśli użytkownik poda plik .xyz, którego nie obsługujemy? 
Zamiast rzucać wszędzie sprawdzanie if (parser != null), zwrócimy Null Object – obiekt, który zachowuje się bezpiecznie, 
nic nie robiąc.

Stwórz 2 klasy odpowiadające sposobom parsowania plików wsadowych csv i json:
1. CsvTransactionParser: `src/main/groovy/pl/edu/praktyki/parser/CsvTransactionParser.groovy` 
2. JsonTransactionParser: `src/main/groovy/pl/edu/praktyki/parser/JsonTransactionParser.groovy`


```groovy
package pl.edu.praktyki.parser

import pl.edu.praktyki.domain.Transaction
import java.time.LocalDate

// Prawdziwa implementacja
class CsvTransactionParser implements TransactionParser {
    @Override
    List<Transaction> parse(File file) {
        println ">>> [CSV PARSER] Czytam plik: ${file.name}"
        // Symulacja parsowania pliku
        return[
            new Transaction(id: "CSV-1", amount: -150.0, category: "Zakupy", date: LocalDate.now())
        ]
    }
}
```

```groovy
package pl.edu.praktyki.parser

import pl.edu.praktyki.domain.Transaction
import java.time.LocalDate

// Prawdziwa implementacja
class JsonTransactionParser implements TransactionParser {
    @Override
    List<Transaction> parse(File file) {
        println ">>> [JSON PARSER] Czytam plik: ${file.name}"
        return[
            new Transaction(id: "JSON-1", amount: 5000.0, category: "Wpływ", date: LocalDate.now())
        ]
    }
}
```

Stwórz plik obslugojący `WZORZEC NULL OBJECT`: Bezpieczna atrapa!

`src/main/groovy/pl/edu/praktyki/parser/UnsupportedFormatParser.groovy`

```groovy
package pl.edu.praktyki.parser

import pl.edu.praktyki.domain.Transaction
import java.time.LocalDate

// WZORZEC NULL OBJECT: Bezpieczna atrapa!
class UnsupportedFormatParser implements TransactionParser {
    @Override
    List<Transaction> parse(File file) {
        println ">>> [BŁĄD] Format pliku ${file.name} nie jest obsługiwany!"
        return[] // Zwraca pustą listę zamiast rzucać NullPointerException!
    }
}
```

Krok 3: Wzorzec Fabryki (Litera 'O' w SOLID)
--------------------------------------------

Fabryka to miejsce, w którym decydujemy, który obiekt stworzyć. 
Dzięki użyciu Mapy w Groovym, nasza Fabryka jest w 100% Zamknięta na modyfikacje.

Stwórz `src/main/groovy/pl/edu/praktyki/parser/ParserFactory.groovy`:

```groovy
package pl.edu.praktyki.parser

class ParserFactory {

    // Rejestr dostępnych parserów (Mapowanie rozszerzenia na obiekt)
    private static final Map<String, TransactionParser> PARSERS =[
        'csv': new CsvTransactionParser(),
        'json': new JsonTransactionParser()
    ]

    /**
     * Wzorzec Factory Method: Zwraca odpowiednią instancję na podstawie parametru.
     */
    static TransactionParser getParserForFile(File file) {
        // Wyciągamy rozszerzenie pliku (np. 'csv')
        String extension = file.name.tokenize('.').last().toLowerCase()

        // Magia Groovy: Jeśli nie ma w mapie, zwróć Null Object!
        return PARSERS[extension] ?: new UnsupportedFormatParser()
    }
}
```

Krok 4: Weryfikacja w Spocku (ParserFactorySpec.groovy)
-------------------------------------------------------

Zobacz, jak czysty i odporny na błędy jest teraz kod, który używa naszej Fabryki.

Stwórz src/test/groovy/pl/edu/praktyki/parser/ParserFactorySpec.groovy:

```groovy
package pl.edu.praktyki.parser

import spock.lang.Specification
import java.nio.file.Files

class ParserFactorySpec extends Specification {

    def "powinien zwrócić odpowiedni parser zgodnie z zasadą Open/Closed"() {
        given: "różne typy plików"
        def csvFile = new File("wyciag.csv")
        def jsonFile = new File("dane.json")
        def unknownFile = new File("wirus.exe")

        when: "prosimy Fabrykę o parsery"
        def parser1 = ParserFactory.getParserForFile(csvFile)
        def parser2 = ParserFactory.getParserForFile(jsonFile)
        def parser3 = ParserFactory.getParserForFile(unknownFile)

        then: "otrzymujemy poprawne implementacje (Zasada Dependency Inversion)"
        parser1 instanceof CsvTransactionParser
        parser2 instanceof JsonTransactionParser
        
        and: "Wzorzec Null Object chroni nas przed nieznanymi formatami"
        parser3 instanceof UnsupportedFormatParser

        and: "wywołanie metody na nieznanym formacie jest bezpieczne i zwraca pustą listę"
        def result = parser3.parse(unknownFile)
        result.isEmpty() == true
    }
}
```

Dlaczego ten kod to esencja SOLID dla Mid-Seniora?
--------------------------------------------------

DIP (Dependency Inversion): Gdybyś wywołał tę fabrykę w Twoim głównym skrypcie CLI, napisałbyś:
`TransactionParser parser = ParserFactory.getParserForFile(myFile)`
Skrypt widzi tylko interfejs TransactionParser. 
Nie ma pojęcia, czy w środku działa CSV, JSON czy XML. Zależy od abstrakcji, a nie od konkretu.

OCP (Open/Closed): 
Co musisz zrobić, gdy szef każe Ci dodać obsługę plików XML?
Tworzysz plik XmlTransactionParser.groovy.
Dopisujesz 'xml': new XmlTransactionParser() do mapy w Fabryce.
Nie dotykasz ani jednej linijki logiki biznesowej, która wywołuje .parse(). Kod jest "Zamknięty" na grzebanie w starej logice.

Null Object Pattern: 
Uwalnia Twój kod od najgorszego zapachu w Javie: if (parser != null) { parser.parse() }. 
Pusty obiekt sam z siebie zachowuje się bezpiecznie, zwracając pustą listę.

Zrozumienie, jak odseparować tworzenie obiektu `Fabryka` od jego używania `Interfejs`, 
to ogromny skok jakościowy w pisaniu oprogramowania.



