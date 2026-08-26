package pl.edu.praktyki.operation

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface OperationRepository extends JpaRepository<OperationEntity, Long> {

    // Znajdź operację po unikalnym ID zewnętrznym
    Optional<OperationEntity> findByOperationId(String operationId)

    // Znajdź wszystkie operacje danego typu (np. "DEPOSIT")
    List<OperationEntity> findByOperationType(String operationType)

    // Znajdź operacje po statusie (np. "NEW", "FAILED")
    List<OperationEntity> findByStatus(String status)

    // Znajdź wszystkie operacje z jednej paczki (correlationId)
    List<OperationEntity> findByCorrelationId(String correlationId)

    // Sprawdź czy operacja już była przetworzona (idempotencja)
    boolean existsByOperationId(String operationId)
}