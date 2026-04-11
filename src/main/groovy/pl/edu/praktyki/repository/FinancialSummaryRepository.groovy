package pl.edu.praktyki.repository
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface FinancialSummaryRepository extends JpaRepository<FinancialSummaryEntity, String> {}