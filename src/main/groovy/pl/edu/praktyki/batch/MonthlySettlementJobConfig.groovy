package pl.edu.praktyki.batch

import jakarta.persistence.EntityManagerFactory
import org.springframework.batch.core.Job
import org.springframework.batch.core.Step
import org.springframework.batch.core.job.builder.JobBuilder
import org.springframework.batch.core.repository.JobRepository
import org.springframework.batch.core.step.builder.StepBuilder
import org.springframework.batch.item.database.JpaPagingItemReader
import org.springframework.batch.item.database.builder.JpaPagingItemReaderBuilder
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.transaction.PlatformTransactionManager
import pl.edu.praktyki.repository.TransactionEntity

/**
 * Lab89 – Spring Batch
 *
 * Konfiguracja miesięcznego joba rozliczeniowego.
 *
 * WAŻNE – Spring Batch 5.x + Spring Boot 3.x:
 *   NIE używamy @EnableBatchProcessing — wyłączyłoby to auto-konfigurację Spring Boota!
 *   Boot automatycznie dostarcza JobRepository, JobLauncher i DataSourceTransactionManager.
 *
 * Architektura:
 *   monthlySettlementJob
 *     └── monthlySettlementStep  (chunk-size = 50)
 *           ├── Reader:    JpaPagingItemReader<TransactionEntity>   – czyta transakcje partiami
 *           ├── Processor: MonthlySettlementItemProcessor           – mapuje 1:1 na encję rozliczeniową
 *           └── Writer:    MonthlySettlementItemWriter              – zapisuje do tabeli monthly_settlement
 */
@Configuration
class MonthlySettlementJobConfig {

    /** Rozmiar chunka – liczba elementów przetwarzanych w jednej transakcji DB */
    static final int CHUNK_SIZE = 50

    @Autowired
    MonthlySettlementItemProcessor monthlySettlementItemProcessor

    @Autowired
    MonthlySettlementItemWriter monthlySettlementItemWriter

    /**
     * ItemReader – używa JPA Paging, czyli pobiera dane ze stronicowaniem (bez OutOfMemoryError
     * nawet przy milionie rekordów).
     *
     * Produkcyjna porada: dodaj @StepScope i filtruj po JobParameter "targetMonth",
     * aby przetwarzać tylko wybrany miesiąc, np.:
     *   .queryString("SELECT t FROM TransactionEntity t WHERE FUNCTION('to_char', t.date, 'YYYY-MM') = :month")
     *   .parameterValues(["month": targetMonth])
     */
    @Bean
    JpaPagingItemReader<TransactionEntity> monthlySettlementReader(EntityManagerFactory entityManagerFactory) {
        new JpaPagingItemReaderBuilder<TransactionEntity>()
                .name("monthlySettlementReader")
                .entityManagerFactory(entityManagerFactory)
                .queryString("SELECT t FROM TransactionEntity t ORDER BY t.date, t.dbId")
                .pageSize(CHUNK_SIZE)
                .build()
    }

    @Bean
    Step monthlySettlementStep(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            JpaPagingItemReader<TransactionEntity> monthlySettlementReader) {

        new StepBuilder("monthlySettlementStep", jobRepository)
                .<TransactionEntity, MonthlySettlementEntity>chunk(CHUNK_SIZE, transactionManager)
                .reader(monthlySettlementReader)
                .processor(monthlySettlementItemProcessor)
                .writer(monthlySettlementItemWriter)
                .build()
    }

    @Bean
    Job monthlySettlementJob(JobRepository jobRepository, Step monthlySettlementStep) {
        new JobBuilder("monthlySettlementJob", jobRepository)
                .start(monthlySettlementStep)
                .build()
    }
}

