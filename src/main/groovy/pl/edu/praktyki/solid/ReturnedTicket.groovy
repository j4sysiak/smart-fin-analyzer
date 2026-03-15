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
