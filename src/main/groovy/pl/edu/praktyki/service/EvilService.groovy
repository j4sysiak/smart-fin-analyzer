package pl.edu.praktyki.service

import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service

@Service
class EvilService {
    @Async("bulkTaskExecutor") // Używamy puli wątków: `bulkTaskExecutor` to nazwa beana typu Executor/TaskExecutor (czyli puli wątków).
    void throwErrorAsync() {
        throw new RuntimeException("KATASTROFA W TLE")
    }
}
