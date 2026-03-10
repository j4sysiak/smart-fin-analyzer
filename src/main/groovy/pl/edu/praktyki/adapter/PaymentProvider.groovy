package pl.edu.praktyki.adapter

// 1. NASZ NOWOCZESNY INTERFEJS
interface PaymentProvider {

    boolean processPayment(String accountId, BigDecimal amount)

    String getStatus()

}