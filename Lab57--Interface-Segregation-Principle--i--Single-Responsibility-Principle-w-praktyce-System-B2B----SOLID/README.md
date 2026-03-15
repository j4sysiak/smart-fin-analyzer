Lab 57
------

Lab57--Interface-Segregation-Principle--i--Single-Responsibility-Principle-w-praktyce-System-B2B----SOLID
---------------------------------------------------------------------------------------------------------

Mamy za sobą O (Open/Closed) i D (Dependency Inversion).
Weźmy na warsztat dwie kolejne litery, które często sprawiają najwięcej problemów w dużych systemach:

`I - Interface Segregation Principle` (Zasada Segregacji Interfejsów): 
Nie zmuszaj klienta do implementowania metod, których nie używa. (Tzw. problem "Grubych Interfejsów").

`S - Single Responsibility Principle` (Zasada Pojedynczej Odpowiedzialności): 
Klasa powinna mieć tylko jeden powód do zmiany.

Lab 57: ISP i SRP w praktyce (System B2B)
-----------------------------------------

Problem (Anty-wzorzec):
Wyobraź sobie, że w naszej aplikacji finansowej pojawia się wymóg obsługi faktur (Invoice) i zwrotów (Refund). 
Junior programista stworzyłby jeden wielki interfejs `FinancialDocument`:

```groovy
interface FinancialDocument {
  BigDecimal getTaxAmount()   // Podatek
  BigDecimal getRefundFee()   // Opłata za zwrot
}
```

Jeśli teraz stworzysz klasę `Invoice` (Faktura), musisz zaimplementować `getRefundFee()`, 
mimo że faktur się w ten sposób nie zwraca! 
Zwracasz więc null albo rzucasz UnsupportedOperationException. 
To jawne łamanie ISP!!!

Rozwiązanie (The SOLID Way):
----------------------------

Podzielimy ten wielki interfejs na małe, precyzyjne "umiejętności".

Krok 1: Małe Interfejsy: `Zasada ISP - Interface Segregation Principle`

Tworzymy małe, dedykowane interfejsy. 
Każdy robi tylko jedną rzecz.

Stwórz plik `src/main/groovy/pl/edu/praktyki/solid/Interfaces.groovy`:

```groovy
package pl.edu.praktyki.solid

// Interfejs tylko dla dokumentów, od których odprowadza się podatek
interface Taxable {
    BigDecimal getTaxAmount()
}

// Interfejs tylko dla dokumentów, które można zwrócić
interface Refundable {
    BigDecimal getRefundAmount()
}
```

Krok 2: Czyste Modele
---------------------

Teraz tworzymy klasy, które implementują tylko to, czego naprawdę potrzebują.

Stwórz plik `src/main/groovy/pl/edu/praktyki/solid/Invoice.groovy`:

```groovy
package pl.edu.praktyki.solid

import groovy.transform.Canonical

@Canonical
class Invoice implements Taxable {
    String id
    BigDecimal netAmount
    BigDecimal taxRate

    @Override
    BigDecimal getTaxAmount() {
        return netAmount * taxRate
    }
}
```

Stwórz plik `src/main/groovy/pl/edu/praktyki/solid/ReturnedTicket.groovy`:

```groovy
package pl.edu.praktyki.solid

import groovy.transform.Canonical

@Canonical
class ReturnedTicket implements Refundable {
    String ticketId
    BigDecimal ticketPrice

    @Override
    BigDecimal getRefundAmount() {
        return ticketPrice * 0.9 // Zwracamy 90% ceny (10% prowizji)
    }
}
```

Stwórz plik `src/main/groovy/pl/edu/praktyki/solid/B2BTransaction.groovy`:

```groovy
package pl.edu.praktyki.solid

import groovy.transform.Canonical

// Magia: B2BTransaction może być OBU typami naraz!
@Canonical
class B2BTransaction implements Taxable, Refundable {
    BigDecimal totalAmount

    @Override
    BigDecimal getTaxAmount() { totalAmount * 0.23 } // 23% VAT

    @Override
    BigDecimal getRefundAmount() { totalAmount } // Pełny zwrot
}
```

Krok 3: Serwis o Pojedynczej Odpowiedzialności - Single Responsibility Principle
--------------------------------------------------------------------------------

Nasz serwis do podatków nie wie nic o zwrotach, fakturach czy biletach. 
On wie tylko, że przyjmuje obiekty typu Taxable. 
Ma jedną odpowiedzialność: liczyć podatki.

Stwórz `src/main/groovy/pl/edu/praktyki/solid/TaxService.groovy`:

```groovy
package pl.edu.praktyki.solid

import org.springframework.stereotype.Service

@Service
class TaxService {

    // Zauważ typ parametru! Przyjmujemy listę obiektów Taxable.
    // Kompilator nawet nie pozwoli nam przekazać tutaj ReturnedTicket!
    BigDecimal calculateTotalTax(List<Taxable> documents) {
        return documents*.taxAmount.sum() ?: 0.0
    }
}
```

Krok 4: Test Spock (Groovy Filter Magic)
----------------------------------------

Tutaj pokażemy, jak elegancko w Groovy można wyfiltrować z "wielkiego wora" różnych dokumentów tylko te, 
które posiadają konkretną cechę (implementują dany interfejs).

Stwórz `src/test/groovy/pl/edu/praktyki/solid/SolidSpec.groovy`:

```groovy
package pl.edu.praktyki.solid

import spock.lang.Specification

class SolidSpec extends Specification {

    def taxService = new TaxService()

    def "powinien wyliczyć podatek tylko z dokumentów, które implementują Taxable (Zasada ISP/SRP)"() {
        given: "worek mieszanych dokumentów z systemu"
        def allDocuments =[
            new Invoice(id: "INV-1", netAmount: 1000.0, taxRate: 0.23),     // Podatek: 230
            new ReturnedTicket(ticketId: "T-99", ticketPrice: 50.0),        // Brak podatku (nie implementuje Taxable!)
            new B2BTransaction(totalAmount: 2000.0)                         // Podatek: 460
        ]

        when: "filtrujemy tylko dokumenty podatkowe używając metody grep()"
        // Groovy Magic: metoda grep() działa jak filter(it -> it instanceof Taxable)
        List<Taxable> taxableDocs = allDocuments.grep(Taxable)

        and: "przekazujemy je do dedykowanego serwisu"
        def totalTax = taxService.calculateTotalTax(taxableDocs)

        then: "lista do opodatkowania ma tylko 2 elementy (Bilet został odrzucony)"
        taxableDocs.size() == 2

        and: "suma podatków to 230 + 460 = 690"
        totalTax == 690.0
    }
}
```

Dlaczego ten kod to mistrzostwo inżynierii (Co zyskaliśmy)?
-----------------------------------------------------------

Interface Segregation (ISP): 
Klasa `ReturnedTicket` jest "czysta". 
Nie ma w sobie pustej metody `getTaxAmount() { return 0 }`. 
Jeśli nie jest opodatkowana, po prostu nie implementuje tego interfejsu.

Single Responsibility (SRP): 
Klasa `TaxService` zajmuje się wyłącznie podatkami. 
Jeśli zmienią się przepisy dotyczące zwrotów biletów, kod `TaxService` nie zostanie nawet dotknięty. 
Zmiana w jednym miejscu systemu nie psuje innego.

Groovy grep(): 
To genialny "szort" z Groovy'ego. 
Wywołanie `list.grep(Klasa)` automatycznie wyciąga z listy tylko te obiekty, 
które są instancją podanej Klasy lub Interfejsu, i zrzutowuje je na odpowiedni typ. Zero instanceof i ręcznego rzutowania!

Zaimplementuj to i odpal test! Zobaczysz, 
jak gładko TaxService ignoruje bilet i skupia się tylko na swoich zadaniach. 


