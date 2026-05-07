package pl.edu.praktyki.batch

import org.springframework.data.jpa.repository.JpaRepository

interface MonthlySettlementRepository extends JpaRepository<MonthlySettlementEntity, Long> {
}

