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