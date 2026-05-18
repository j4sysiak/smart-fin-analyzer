package pl.edu.praktyki.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Clock

@Configuration
class AppConfig {

    @Bean
    Clock clock() {
        // Domyślnie systemowy zegar ze strefą czasową hosta
        return Clock.systemDefaultZone()
    }
}