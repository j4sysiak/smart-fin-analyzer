package pl.edu.praktyki.repository

import jakarta.persistence.*

@Entity
@Table(name = "counters")

class Counter {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id

    @Column(unique = true, nullable = false)
    private String name

    @Column(name = "counter_value")
    private int value = 0

    Long getId() { return id }
    String getName() { return name }
    void setName(String name) { this.name = name }
    int getValue() { return value }
    void setValue(int value) { this.value = value }
}
