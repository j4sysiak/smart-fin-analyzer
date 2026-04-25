package pl.edu.praktyki.security

import jakarta.persistence.*
import groovy.transform.Canonical

@Entity
@Table(name = "users")
@Canonical
class UserEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id

    @Column(unique = true, nullable = false)
    String username

    @Column(nullable = false)
    String password

    @Column(nullable = false)
    String role

    UserEntity() {} // Wymagane przez JPA
}