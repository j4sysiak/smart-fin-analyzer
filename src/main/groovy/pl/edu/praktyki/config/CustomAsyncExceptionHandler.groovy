package pl.edu.praktyki.config

import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import pl.edu.praktyki.service.ThreadTracker
import groovy.util.logging.Slf4j
import java.lang.reflect.Method

@Slf4j
@Component
class CustomAsyncExceptionHandler implements AsyncUncaughtExceptionHandler {

    @Autowired
    ThreadTracker threadTracker

    @Override
    void handleUncaughtException(Throwable ex, Method method, Object... params) {
        log.error(">>> [ASYNC-FATAL] Wyjątek w metodzie asynchronicznej: {}", method.name)
        log.error(">>> Komunikat błędu: {}", ex.message)

        // Zapisujemy informację o awarii do naszego trackera,
        // żeby administrator widział błąd w REST API /monitoring/threads
        threadTracker.put("ERROR.${method.name}", [
                timestamp: System.currentTimeMillis(),
                exception: ex.class.simpleName,
                message: ex.message,
                thread: Thread.currentThread().name
        ])
    }
}