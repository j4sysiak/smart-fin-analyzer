package pl.edu.praktyki.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableAsync
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor

import java.util.concurrent.Executor

@Configuration
// Włącza obsługę adnotacji @Async.
// Adnotacje takie jak @Async czy @Transactional tworzą "opakowanie" wokół Twojej klasy.
@EnableAsync
class AsyncConfig {

    @Bean(name = "bulkTaskExecutor")
    Executor bulkTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor()
        executor.corePoolSize = 2       // Ile wątków zawsze czeka
        executor.maxPoolSize = 5        // Maksymalnie ile wątków może powstać
        executor.queueCapacity = 500    // Ile zadań może czekać w kolejce
        executor.setThreadNamePrefix("BulkExecutor-")
        executor.initialize()
        return executor
    }
}