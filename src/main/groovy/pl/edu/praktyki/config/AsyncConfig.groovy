package pl.edu.praktyki.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableAsync
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.Executor

@Configuration


// Adnotacje takie jak @Async czy @Transactional tworzą "opakowanie" wokół Twojej klasy.
// Chodzi o klasę będącą beanem Springa — czyli klasę zarządzaną przez kontener
// (np. oznaczoną @Component, @Service, @Repository, @Configuration albo zdefiniowaną jako @Bean).
// Adnotacje takie jak @Async czy @Transactional działają przez utworzenie proxy wokół tego beana
// i przechwytywanie wywołań metod przychodzących z zewnątrz.

// Włącza obsługę adnotacji @Async.
// Dzięki temu możesz oznaczać metody jako asynchroniczne, a Spring będzie je wykonywał w osobnych wątkach.
@EnableAsync
class AsyncConfig {

    @Bean(name = "bulkTaskExecutor")
    Executor bulkTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor()

        // Konfiguracja sprzętowa - zwiększona pula dla testów integracyjnych i dużych batchy
        executor.corePoolSize = 8
        executor.maxPoolSize = 16
        executor.queueCapacity = 2000 // duża kolejka, żeby nie blokować przyjmowania dużych paczek

        executor.setThreadNamePrefix("BulkAsync-")

        // MAGIA MIDA: Co zrobić, gdy maxPoolSize i queueCapacity są pełne?
        // CallerRunsPolicy: Wątek, który wysłał zadanie (np. HTTP thread),
        // sam musi je wykonać. To naturalny hamulec dla systemu!

        // Ustawiając CallerRunsPolicy, implementujemy mechanizm, który automatycznie spowalnia przyjmowanie nowych danych,
        // gdy serwer nie wyrabia.

        // To znaczy, że gdy puli wątków i kolejka będą pełne, zadanie nie zostanie odrzucone ani wrzucone do innej kolejki
        // — wątek, który wysłał zadanie (np. wątek obsługi żądania HTTP), sam wykona to zadanie.
        // To działa jako naturalny mechanizm throttlingu: spowalnia przyjmowanie nowych pracy zamiast odrzucać zadania.
        // Plus: unikasz odrzuceń i nagłego wzrostu pamięci;
        // Minus: wątek wywołujący może się blokować i zwiększyć latencję.
        // Linia ustawia tę politykę dla executora.

        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy())

        executor.initialize()
        return executor
    }
}