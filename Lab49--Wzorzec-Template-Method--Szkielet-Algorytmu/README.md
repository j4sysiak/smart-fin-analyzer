Lab 49
------

Wzorzec Template Method (Metoda Szablonowa) to jeden z najbardziej eleganckich wzorców. 
Pozwala zdefiniować `szkielet` algorytmu w klasie bazowej, pozostawiając szczegóły implementacji klasom pochodnym.

W Javie musiałbyś stworzyć klasę abstrakcyjną i wymuszać implementację metod abstrakcyjnych. 
W Groovym zrobimy to jeszcze lepiej, łącząc `klasy abstrakcyjne` z `Traitami`, aby kod był maksymalnie "używalny ponownie".

Lab 38: Wzorzec Template Method – Szkielet Algorytmu
----------------------------------------------------

Cel: 
Stworzymy proces generowania raportu, który zawsze składa się z 3 kroków: 
`PobierzDane -> Przetwórz -> Zapisz`. 
Zmieniać się może tylko sposób pobierania i zapisu.

Krok 1: Klasa Szablonowa (ReportTemplate.groovy)
------------------------------------------------

Stwórz plik `src/main/groovy/pl/edu/praktyki/report/ReportTemplate.groovy`.

```groovy
package pl.edu.praktyki.report

abstract class ReportTemplate {

    // TO JEST METODA SZABLONOWA (Template Method)
    // Jest 'final', więc klasy pochodne nie mogą jej zepsuć - wymuszamy kolejność!
    final void generateReport() {
        def data = fetchData()  // klasy pochodne muszą zaimplementować
        def processed = processData(data) // klasy pochodne muszą zaimplementować
        saveReport(processed)  // można nadpisać
    }

    // Kroki, które klasy pochodne muszą zaimplementować (wymuszone abstract)
    abstract String fetchData()
    abstract String processData(String data)
    
    // Krok, który ma domyślną implementację (można nadpisać)
    void saveReport(String report) {
        println "Zapisuję raport domyślnie do konsoli: $report"
    }
}
```

Krok 2: Konkretne Implementacje
-------------------------------

Stwórz `src/main/groovy/pl/edu/praktyki/report/HtmlReport.groovy`.

```groovy
class HtmlReport extends ReportTemplate {
@Override
String fetchData() { "Dane z bazy SQL" }

    @Override
    String processData(String data) { "<html><body>$data</body></html>" }

    @Override
    void saveReport(String report) {
        println "Zapisuję raport HTML: $report"
    }
}
```


Oto implementacja CsvReport.groovy. 
W przeciwieństwie do HtmlReport, tutaj skupiamy się na sformatowaniu danych jako wartości oddzielone przecinkami (CSV), 
co jest typowym wymaganiem w systemach analitycznych (np. eksport do Excela).

Stwórz plik `src/main/groovy/pl/edu/praktyki/report/CsvReport.groovy`:

```groovy
package pl.edu.praktyki.report

class CsvReport extends ReportTemplate {

    @Override
    String fetchData() {
        // Symulacja pobrania danych, które w CSV będą miały nagłówek i wiersz
        return "ID,KWOTA,DATA\n1,500.00,2026-03-10"
    }

    @Override
    String processData(String data) {
        // Tutaj moglibyśmy np. zamienić kropki na przecinki, 
        // żeby plik był kompatybilny z polskim Excelem
        return data.replace('.', ',')
    }

    @Override
    void saveReport(String report) {
        println "Zapisuję raport CSV do pliku: $report"
        // W prawdziwym projekcie tutaj użyłbyś: new File("raport.csv").text = report
    }
}
```

Dlaczego to jest fajny przykład do Template Method?

Formatowanie danych: 
HtmlReport produkuje tagi <html>, a CsvReport produkuje nagłówki ID,KWOTA,DATA. 
Szkielet `ReportTemplate` nie musi wiedzieć, jak wygląda formatowanie – on tylko zarządza kolejnością wywołań.

Czystość: 
Klasa CsvReport nie zawiera żadnego kodu sterującego (pętli, logiki if-else). 
Zawiera tylko co ma być zrobione w każdym kroku.

Łatwość rozbudowy: 
Jeśli klient powie: "Chcemy teraz raport w formacie JSON", tworzysz klasę `JsonReport`, 
implementujesz 3 metody i masz gotowy moduł, który na pewno zadziała, bo "szkielet" mu na to pozwoli.

Wyzwanie dla Ciebie:

Spróbuj w klasie ReportTemplate dodać metodę z domyślną implementacją:

```groovy
void logStart() {
println "--- Rozpoczęto generowanie raportu: ${this.getClass().simpleName} ---"
}
```

A potem wywołaj ją wewnątrz final void generateReport().

Czy wiesz, gdzie wstawić wywołanie tej metody logStart(), żeby zawsze wykonywała się jako pierwsza w szablonie? Daj znać i spróbuj to skompilować! 🚀

Możesz teraz dodać do `ReportTemplat`e metodę boolean `shouldLog() { true }`, którą klasy pochodne mogą nadpisać,
by wyłączyć logowanie dla konkretnego typu raportu.



Krok 3: Test Spock (ReportTemplateSpec.groovy)
----------------------------------------------

Testowanie szablonów jest bardzo łatwe, bo testujemy tylko "widzialne" efekty działania metody szablonowej.


test dla HtmlReport będzie prosty – sprawdzimy, czy metoda generateReport() wykonuje się bez błędów i czy wywołuje saveReport().
```groovy
package pl.edu.praktyki.report

import spock.lang.Specification

class ReportTemplateSpec extends Specification {

    def "powinien poprawnie wygenerować raport HTML"() {
        given: "nasz szablon HTML"
        def report = new HtmlReport()

        when: "wywołujemy szablon"
        // Zwróć uwagę, że nie wołamy metod pośrednich, tylko szkielet
        report.generateReport()

        then: "wszystko zadziałało w narzuconej kolejności"
        // Tutaj w prawdziwym teście sprawdzilibyśmy np. plik na dysku
        // lub stan obiektu
        noExceptionThrown()
    }
}
```

test dla CsvReport będzie podobny, ale sprawdzimy, czy dane zostały poprawnie przetworzone (kropki zamienione na przecinki) 
i czy metoda saveReport wypisała odpowiedni komunikat.

```groovy
package pl.edu.praktyki.report

import spock.lang.Specification

class CsvReportSpec extends Specification {

    def "powinien poprawnie wygenerować raport CSV z przecinkami zamiast kropek"() {
        given: "instancja raportu CSV"
        def csvReport = new CsvReport()

        when: "uruchamiamy cały proces szablonowy"
        // Zauważ, że wołamy metodę finalną z klasy bazowej,
        // a ona wewnątrz używa naszych metod z CsvReport
        csvReport.generateReport()

        then: "zwracamy wynik do weryfikacji"
        // Aby ułatwić sobie testowanie, możemy "wyłapać" co metoda saveReport wypisała
        // albo dodać do klasy CsvReport metodę zwracającą wynik

        // Zróbmy małą sprytną weryfikację:
        def rawData = csvReport.fetchData()
        def processedData = csvReport.processData(rawData)

        processedData.contains("1,500,00,2026-03-10") // Sprawdzamy czy kropka zmieniła się w przecinek
        processedData.startsWith("ID,KWOTA,DATA")
    }

    def "powinien wypisać raport na konsolę"() {
        given:
        def csvReport = new CsvReport()
        def out = new ByteArrayOutputStream()
        System.setOut(new PrintStream(out)) // Przekierowanie konsoli do strumienia

        when:
        csvReport.saveReport("MOCK_REPORT")

        then:
        out.toString().contains("Zapisuję raport CSV do pliku: MOCK_REPORT")

        cleanup:
        System.setOut(System.out) // Koniecznie przywróć standardową konsolę!

    }

}
```

Dlaczego to jest wzorzec "dla Mid-a"?

Enkapsulacja procesu (Control Flow): 
W dużej firmie masz 50 typów raportów. 
Jeśli pozwolisz każdemu programiście pisać własną metodę `generateReport()`, to jeden zapomni o zapisie do bazy, inny zapomni o logowaniu. 
`final void generateReport()` gwarantuje, że wszyscy wykonują kroki w tej samej kolejności.

Code Reuse (Używalność): 
Jeśli 40 z 50 raportów zapisuje się tak samo, to implementujesz `saveReport()` w klasie bazowej. 
Zmieniasz jedną linię w klasie bazowej i 40 raportów nagle zaczyna działać inaczej (np. wysyłać maila zamiast pisać do pliku).

Haki (Hooks):
Możesz dodać do `ReportTemplat`e metodę boolean `shouldLog() { true }`, którą klasy pochodne mogą nadpisać, 
by wyłączyć logowanie dla konkretnego typu raportu.


Wyzwanie Finałowe Lab 38:
-------------------------

Twoim wyzwaniem jest użycie `Traitów` do zrobienia tego samego, co zrobiłeś klasą abstrakcyjną.
Stwórz trait ReportGenerator.
Zaimplementuj w nim default void generateReport() { fetch(); process(); save() }.
Użyj tego traita w klasie FinanceReport.

Zaleta: 
W klasie FinanceReport będziesz mógł zaimportować ten trait implements ReportGenerator, ale jeśli będziesz potrzebował też innego traita (np. AuditLog), po prostu dopiszesz implements ReportGenerator, AuditLog. W klasie abstrakcyjnej (z Kroku 1) nie mógłbyś tak łatwo łączyć zachowań.

Czy wiesz już, jak stworzyć Trait, który zawiera domyślną metodę z "szkieletem" algorytmu? 
To jest esencja nowoczesnego programowania w Groovy! Daj znać, czy chcesz spróbować!

Rozwiazanie wyzwania z Traitami:
--------------------------------

To jest świetne wyzwanie, bo pozwala zrozumieć tzw. "Composition over Inheritance" (kompozycję zamiast dziedziczenia)
– koncepcję, za którą architekci oprogramowania oddaliby wiele!


co to jest Trait?
-----------------
`Trait` to specjalny rodzaj "interfejsu z ciałem" w Groovy, który pozwala na definiowanie metod z implementacją, 
a także abstrakcyjnych metod, które muszą być zaimplementowane przez klasę, która go używa. 
To coś pomiędzy interfejsem a klasą abstrakcyjną, ale z większą elastycznością.

W przeciwieństwie do klas abstrakcyjnych, Trait pozwala Ci "wstrzyknąć" zachowanie do dowolnej klasy, nawet jeśli ta klasa dziedziczy już po czymś innym.

Krok 1: Definicja Traita z "Szkieletem" (ReportGenerator.groovy)
----------------------------------------------------------------

Stwórz plik `src/main/groovy/pl/edu/praktyki/report/ReportGenerator.groovy`. 
Metoda generateReport używa tutaj tzw. self-types 
(albo po prostu wywołuje metody, które klasa implementująca musi posiadać).

```groovy
package pl.edu.praktyki.report

trait ReportGenerator {

    // Szkielet algorytmu (Metoda Szablonowa wewnątrz Traita)
    void generateReport() {
        println ">>> [TRAIT] Rozpoczynam generowanie..."
        def data = fetchData()
        def processed = processData(data)
        saveReport(processed)
        println ">>> [TRAIT] Raport gotowy!"
    }

    // Wymagamy, aby klasa implementująca miała te metody
    abstract String fetchData()
    abstract String processData(String data)
    
    // Opcjonalna domyślna implementacja (można nadpisać w klasie)
    void saveReport(String report) {
        println "Zapisuję do pliku: $report"
    }
}
```

Krok 2: Implementacja w klasie (FinanceReport.groovy)
-----------------------------------------------------

Teraz zobacz, jak elegancko możemy "dokleić" logowanie (AuditLog z Labu 19!) do naszego raportu.

Stwórz plik `src/main/groovy/pl/edu/praktyki/report/FinanceReport.groovy`:

```groovy
package pl.edu.praktyki.report

// Wykorzystujemy nasz Trait z audytu (zrobiony wcześniej)
import pl.edu.praktyki.trait.AuditLog

class FinanceReport implements ReportGenerator, AuditLog {

    @Override
    String fetchData() { "Dane finansowe z bazy" }

    @Override
    String processData(String data) { "Raport: $data przeliczony" }

    // Możemy nadpisać saveReport, ale nie musimy
}
```


Krok 3: Test Spock (ReportTraitSpec.groovy)
-------------------------------------------

Sprawdźmy, czy klasa FinanceReport faktycznie zyskała umiejętności z obu Traitów jednocześnie.

```groovy
package pl.edu.praktyki.report

import spock.lang.Specification

class ReportTraitSpec extends Specification {

    def "powinien wykorzystać metody z obu traitów"() {
        given:
        def report = new FinanceReport()

        when: "wywołujemy metodę z ReportGenerator"
        report.generateReport()
        
        and: "wywołujemy metodę z AuditLog"
        report.audit("Sprawdzenie poprawności raportu")

        then: "obie funkcjonalności działają"
        report.hasAuditEntry("Sprawdzenie poprawności raportu")
        // Sprawdzamy czy metoda z traita faktycznie istnieje
        report.respondTo("generateReport") != null
    }
}
```

Dlaczego to jest potężne (Senior perspective)?

Brak dziedziczenia po jednej klasie: W Javie, jeśli klasa FinanceReport dziedziczyłaby po BaseReport, nie mogłaby dziedziczyć po niczym innym. Dzięki Traitom możesz dodać ReportGenerator, AuditLog, JsonExporter, XmlExporter... – Twoja klasa staje się "super-klasą" dzięki kompozycji cech.

Domyślna implementacja (Defaults): Traity pozwalają na definiowanie kodu, który działa "od razu". Jeśli klasa nie nadpisze saveReport, użyje wersji z traita.

Wielodziedziczenie zachowań: To najczystsza forma implementacji interfejsów w JVM.

Wyzwanie:
Czy widzisz teraz różnicę? Gdybyś użył klasy abstrakcyjnej, Twoja klasa FinanceReport byłaby "związana" z ReportTemplate. Teraz jest "wolna", a ReportGenerator to tylko "dodatek", który w każdej chwili możesz odpiąć.

ale po co w ogóle korzystamy tutaj z tej klasy AuditLog ?
---------------------------------------------------------

To doskonałe pytanie, które uderza w sedno "Czystej Architektury" (Clean Architecture).
W Javie (i w Groovym też) masz dwa rodzaje logiki:

1. Logika Biznesowa (Core): To, co robi Twój system (np. "przelicz walutę", "wygeneruj raport").
2. Logika Techniczna (Cross-Cutting Concerns): To, co musi się dziać "w tle", ale nie jest bezpośrednim zadaniem Twojego serwisu (np. "zapisz do logów, że ktoś wywołał tę metodę", "sprawdź uprawnienia", "mierzy czas wykonania").

Oto dlaczego `AuditLog` jako `Trait` jest tu genialnym rozwiązaniem:

1. Oddzielenie "Co" od "Jak"

FinanceReport (albo OrderService) ma być ekspertem od finansów. 
Nie powinien wiedzieć JAK się loguje do pliku, JAK formatuje datę, ani GZIE przechowuje historię.
Gdybyś wsadził audit(...) bezpośrednio do FinanceReport, 
to ta klasa stałaby się śmietnikiem – mieszałbyś obliczenia finansowe z obsługą plików czy konsoli.

2. Wielokrotne użycie (Reusability)

Wyobraź sobie, że masz 10 różnych klas: FinanceReport, InvoiceService, UserRegistration, InventoryManager.
Bez Traita: 
Musiałbyś w każdej z tych 10 klas wkleić ten sam kod do logowania lub dziedziczyć po jakiejś "SuperKlasie", 
co szybko prowadzi do bałaganu (tzw. "God Object").

Z Traitem AuditLog: 
Po prostu dopisujesz implements `AuditLog` do każdej z tych klas. 
Masz 10 klas, które mają tę samą funkcjonalność, ale zachowują swoją czystą, biznesową naturę.

3. Łatwiejsze testowanie (Separation of Concerns)

Zauważ, że w teście `ReportTraitSpec.groovy` przetestowałeś logowanie bez uruchamiania logiki generowania raportu. 
Mógłbyś w przyszłości stworzyć testy tylko dla AuditLog i mieć pewność, że działa on dobrze we wszystkich klasach, które go używają.

4. "Composition over Inheritance" (Kompozycja ponad dziedziczenie)

To jest najważniejszy argument dla Mid-Seniora.

W klasie abstrakcyjnej możesz dziedziczyć tylko raz (Java/Groovy nie pozwalają na wielokrotne dziedziczenie klas).

Traitów możesz użyć nieskończenie wiele!
----------------------------------------
Klasa FinanceReport może jednocześnie:

1. implements ReportGenerator (strategia raportowania)
2. implements AuditLog (logowanie)
3. implements JsonSerializable (formatowanie do JSON)

Realny przykład z życia (Enterprise):

W korporacyjnych systemach mamy bardzo rygorystyczne wymogi prawne (np. RODO/GDPR):
"Każda operacja finansowa musi być zapisana w logu audytowym".

Jeśli użyjesz Traita AuditLog, wystarczy, że administrator (lub architekt) sprawdzi, 
czy wszystkie serwisy finansowe mają implements AuditLog. 
Jeśli tak – ma gwarancję, że nic nie umknie audytowi. 
Gdybyś to pisał "ręcznie" w każdej klasie, na pewno ktoś by o tym zapomniał.

Zrozumiałeś teraz, że AuditLog to nie jest po prostu "klasa pomocnicza", tylko „polisa ubezpieczeniowa” Twojego kodu?

Jeśli chcesz, możemy teraz w ostatnim kroku zrobić AOP (Aspect Oriented Programming) 
------------------------------------------------------------------------------------

AOP - to jest technologia, która robi to, co `AuditLog`, ale bez dopisywania czegokolwiek do klas. 
To absolutny poziom "magiczny" Springa. Chcesz to zobaczyć?

zrobie to w osobnym labie, bo to już jest naprawdę zaawansowane.
