package pl.edu.praktyki.state

// Interfejs opisujący, co można zrobić z transakcją
interface TransactionState {
    void verify(StatefulTransaction tx)
    void process(StatefulTransaction tx)
    void cancel(StatefulTransaction tx)
}