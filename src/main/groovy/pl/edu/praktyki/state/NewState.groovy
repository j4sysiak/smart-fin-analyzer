package pl.edu.praktyki.state

class NewState implements TransactionState {
    @Override
    void verify(StatefulTransaction tx) {
        println ">>> Weryfikacja pozytywna. Zmieniam stan na: Verified."
        tx.currentState = new VerifiedState() // ZMIANA STANU
    }

    @Override
    void process(StatefulTransaction tx) {
        throw new IllegalStateException("Nie można przetworzyć NOWEJ transakcji. Najpierw zweryfikuj!")
    }

    @Override
    void cancel(StatefulTransaction tx) {
        println ">>> Anulowano nową transakcję."
        tx.currentState = new CancelledState()
    }
}