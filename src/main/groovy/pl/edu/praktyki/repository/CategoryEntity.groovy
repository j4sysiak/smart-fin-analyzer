package pl.edu.praktyki.repository

import jakarta.persistence.*
import groovy.transform.Canonical
import org.hibernate.envers.Audited // <--- DODAJ IMPORT (Lab88)

@Entity
@Table(name = "categories")
// @Audited AKTYWNE: CategoryEntity ma tylko proste kolumny (brak @ElementCollection).
// Hibernate Envers 6.4.1 obsługuje tę encję poprawnie — brak ClassCastException.
// TransactionEntity (z @ElementCollection na tags) NIE ma @Audited — bug HHH-17024.
@Audited
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