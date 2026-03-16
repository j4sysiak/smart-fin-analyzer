package pl.edu.praktyki.singleton

import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

@Service
class UserSessionService {
    private final ConcurrentHashMap<String, Integer> loginAttempts = new ConcurrentHashMap<>();

    void recordAttempt(String username) {
        loginAttempts.merge(username, 1, Integer::sum);
    }

    int getAttempts(String username) {
        return loginAttempts.getOrDefault(username, 0);
    }
}
