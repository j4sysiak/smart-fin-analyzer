package pl.edu.praktyki.service

import org.springframework.stereotype.Service
import org.springframework.context.event.EventListener
import pl.edu.praktyki.event.TransactionImportedEvent

@Service
class TransactionAuditListener {

    @EventListener
    void onNewTransaction(TransactionImportedEvent event) {
        // Ta metoda uruchomi się SAMA, gdy ktoś wyśle zdarzenie!
        println ">>> [ZDARZENIE W TLE] System zauważył nową transakcję: ${event.transaction.id}"
    }
}