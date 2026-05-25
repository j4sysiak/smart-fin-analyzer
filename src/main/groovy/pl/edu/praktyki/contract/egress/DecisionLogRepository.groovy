package pl.edu.praktyki.contract.egress

import org.springframework.data.jpa.repository.JpaRepository

interface DecisionLogRepository extends JpaRepository<DecisionLogEntity, Long> {

    // do asercji w testach: ile zapisów dla danego correlationId
    long countByCorrelationId(String correlationId)

    // pomocnicze do e2e inspekcji w teście
    Optional<DecisionLogEntity> findFirstByCorrelationIdOrderByLoggedAtAsc(String correlationId)
}