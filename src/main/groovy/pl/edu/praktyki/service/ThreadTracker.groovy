package pl.edu.praktyki.service

import org.springframework.stereotype.Component

import java.util.concurrent.ConcurrentHashMap

/**
 * Prosty bean do śledzenia informacji o wątkach uruchamiających asynchroniczne zadania.
 * Używany zamiast duplikowania ConcurrentHashMap w wielu klasach.
 */
@Component
class ThreadTracker {
    // przechowujemy obiekty (mapy) z dodatkowymi metadanymi: thread, timestamp, opcjonalnie eventId/user
    private final Map<String, Object> map = new ConcurrentHashMap<>()

    void put(String key, Object value) {
        map.put(key, value)
    }

    Object get(String key) {
        return map.get(key)
    }

    void remove(String key) {
        map.remove(key)
    }

    Map<String, Object> snapshot() {
        return new HashMap<>(map)
    }
}

