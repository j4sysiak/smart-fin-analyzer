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