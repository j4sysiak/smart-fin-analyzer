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