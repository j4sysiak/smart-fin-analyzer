package pl.edu.praktyki.state

class VerifiedState implements TransactionState {
    @Override
    void verify(StatefulTransaction tx) {
        println ">>> Transakcja już jest zweryfikowana." // Ignorujemy
    }

    @Override
    void process(StatefulTransaction tx) {
        println ">>> Przetwarzanie zakończone sukcesem. Transakcja opłacona."
        // W prawdziwym życiu tu wołalibyśmy np. Payment API
    }

    @Override
    void cancel(StatefulTransaction tx) {
        println ">>> Anulowano zweryfikowaną transakcję. Wysyłam powiadomienie."
        tx.currentState = new CancelledState()
    }
}
