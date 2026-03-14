package pl.edu.praktyki.state

class CancelledState implements TransactionState {
    @Override
    void verify(StatefulTransaction tx) { throw new IllegalStateException("Transakcja anulowana.") }

    @Override
    void process(StatefulTransaction tx) { throw new IllegalStateException("Transakcja anulowana.") }

    @Override
    void cancel(StatefulTransaction tx) { println ">>> Już jest anulowana." }
}
