package pl.edu.praktyki.closure

/**
 * Klasa produkcyjna pokazująca 3 praktyczne zastosowania closure.
 *
 * Zamiast tworzyć osobną metodę dla każdej logiki,
 * przekazujemy "co ma się stać" jako closure — z zewnątrz.
 */
class TransactionProcessor {

    // ─────────────────────────────────────────────
    // PRZYKŁAD 1: RETRY
    // Jeśli operacja rzuci wyjątek, spróbuj ponownie.
    // "Co ma się stać" przekazujemy jako closure.
    // ─────────────────────────────────────────────
    def withRetry(int times, Closure operation) {
        int attempt = 0
        while (attempt < times) {
            try {
                return operation()   // uruchamiam to, co dostałem w Closure
            } catch (Exception e) {
                attempt++
                if (attempt == times) throw e
            }
        }
    }

    // ─────────────────────────────────────────────
    // PRZYKŁAD 2: WALIDATOR
    // Sprawdź transakcję dowolną logiką — reguła
    // przychodzi z zewnątrz jako closure.
    // ─────────────────────────────────────────────
    boolean validate(Map transaction, Closure rule) {
        return rule(transaction)         // uruchamiam regułę na transakcji
    }

    // ─────────────────────────────────────────────
    // PRZYKŁAD 3: TIMER
    // Zmierz czas wykonania dowolnego kodu.
    // "Co mierzyć" przekazujemy jako closure.
    // ─────────────────────────────────────────────
    long measureTime(Closure operation) {
        long start = System.currentTimeMillis()
        operation() // uruchamiam to, co dostałem jako Closure: `Thread.sleep(50)` --> symulacja przetwarzania, czekaj po prostu 50 sek.
        return System.currentTimeMillis() - start
    }
}

