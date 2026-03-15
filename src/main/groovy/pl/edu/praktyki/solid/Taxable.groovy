package pl.edu.praktyki.solid

// Interfejs tylko dla dokumentów, od których odprowadza się podatek
interface Taxable {
    BigDecimal getTaxAmount()
}