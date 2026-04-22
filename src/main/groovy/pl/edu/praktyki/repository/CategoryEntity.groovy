package pl.edu.praktyki.repository

import jakarta.persistence.*
import groovy.transform.Canonical

@Entity
@Table(name = "categories")
@Canonical
class CategoryEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id

    @Column(unique = true, nullable = false)
    String name

    // JAWNIE wskazujemy nazwę kolumny z pliku SQL (V8)
    @Column(name = "monthly_limit", nullable = false)
    BigDecimal monthlyLimit

    // JPA potrzebuje pustego konstruktora
    CategoryEntity() {}
}