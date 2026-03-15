package pl.edu.praktyki.solid

// Interfejs tylko dla dokumentów, które można zwrócić
interface Refundable {
    BigDecimal getRefundAmount()
}