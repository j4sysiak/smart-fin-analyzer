package pl.edu.praktyki.contract.egress.outbox

import jakarta.persistence.LockModeType
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

import java.time.Instant


// ten interface jest rozszerzeniem Spring Data JPA Repository,
// który umożliwia wykonywanie operacji CRUD na tabeli "egress_outbox" w bazie danych.
interface EgressOutboxRepository extends JpaRepository<EgressOutboxEntity, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        select o
        from EgressOutboxEntity o
        where o.status in :statuses
          and o.nextAttemptAt <= :now
        order by o.id asc
    """)
    List<EgressOutboxEntity> lockBatchForDispatch(
            @Param("statuses") Collection<EgressOutboxStatus> statuses,
            @Param("now") Instant now,
            Pageable pageable
    )

    long countByStatus(EgressOutboxStatus status)
}