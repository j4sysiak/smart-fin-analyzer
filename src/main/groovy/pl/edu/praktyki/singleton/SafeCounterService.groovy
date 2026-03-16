package pl.edu.praktyki.singleton

import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.stereotype.Service;

@Service
class SafeCounterService {
    private final AtomicInteger counter = new AtomicInteger(0);

    void increment() {
        counter.incrementAndGet(); // operacja atomowa, bezpieczna wątkowo
    }

    int getCounter() {
        return counter.get();
    }
}