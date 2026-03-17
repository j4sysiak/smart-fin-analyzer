package pl.edu.praktyki.singleton

import java.util.concurrent.ConcurrentHashMap
import org.springframework.stereotype.Service

@Service
class UserSessionService {
    private final ConcurrentHashMap<String, Integer> loginAttempts = new ConcurrentHashMap<>()

    void recordAttempt(String username) {
        /*
          Ta linia poniżej ( loginAttempts.merge(username, 1, Integer::sum))
          używa metody merge z ConcurrentHashMap, która działa następująco:
           - username — klucz w mapie
           - 1 — wartość do wstawienia, jeśli klucz jeszcze nie istnieje
          Integer::sum — funkcja scalająca (BiFunction),
             wywoływana gdy klucz już istnieje w mapie — sumuje istniejącą wartość z nową (1)
          W praktyce:
          -----------
          Jeśli username nie istnieje w mapie → wstawia parę (username, 1)
          Jeśli username już istnieje → zwiększa aktualną wartość o 1 (np. 2 + 1 = 3)
          Jest to thread-safe sposób na zliczanie prób logowania per użytkownik, ponieważ ConcurrentHashMap.merge() wykonuje operację atomowo.
        */
        loginAttempts.merge(username, 1, Integer::sum)
    }

    int getAttempts(String username) {
        return loginAttempts.getOrDefault(username, 0)
    }
}