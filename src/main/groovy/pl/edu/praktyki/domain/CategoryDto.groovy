package pl.edu.praktyki.domain

import groovy.transform.Canonical

@Canonical
class CategoryDto {
    Long id
    String name
    BigDecimal monthlyLimit
}

