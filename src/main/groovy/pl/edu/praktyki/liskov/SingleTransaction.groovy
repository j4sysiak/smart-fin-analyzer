package pl.edu.praktyki.liskov

import groovy.transform.Canonical
import pl.edu.praktyki.liskov.FinancialAsset

// 1. LIŚĆ (Leaf) - Pojedyncza, prosta wartość
@Canonical
class SingleTransaction implements FinancialAsset {
    String id
    BigDecimal amount

    @Override
    BigDecimal getValue() {
        return amount
    }
}