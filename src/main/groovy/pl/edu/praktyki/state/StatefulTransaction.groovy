package pl.edu.praktyki.state

class StatefulTransaction {
    String id
    // Transakcja zawsze zaczyna jako "Nowa"
    TransactionState currentState = new NewState()

    void verify() {
        currentState.verify(this)
    }

    void process() {
        currentState.process(this)
    }

    void cancel() {
        currentState.cancel(this)
    }
}