package pl.edu.praktyki.liskov

// Nasza abstrakcja. Każdy obiekt implementujący ten interfejs
// GWARANTUJE, że poprawnie zwróci swoją wartość. Zero wyjątków.
interface FinancialAsset {

    BigDecimal getValue()
}