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

        /*
        Mechanizm krok po kroku:
          1. grep() iteruje po każdym elemencie listy allDocuments.
          2. Dla każdego elementu wywołuje Taxable.isCase(element).
          3. Class.isCase(obj) w Groovy deleguje do Taxable.class.isInstance(obj).
          4. isInstance() zwraca true, jeśli obiekt implementuje dany interfejs — dokładnie jak instanceof.
                W kontekście testu:
                1. Invoice implementuje Taxable → ✅ przechodzi
                2. ReturnedTicket nie implementuje Taxable → ❌ odrzucony
                3. B2BTransaction implementuje Taxable → ✅ przechodzi
         */

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