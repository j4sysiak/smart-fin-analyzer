package pl.edu.praktyki.operation

import groovy.util.logging.Slf4j
import org.springframework.stereotype.Component

/**
 * Cel: closure dispatcher, który dostaje jeden OperationDto i wywołuje właściwą logikę zależnie od typu.
 * Dispatcher operacji wsadowych oparty na Closure (LAB101 — praktyczne użycie).
 *
 * Zamiast pisać:
 *   processDeposit(op)
 *   processWithdrawal(op)
 *   processTransfer(op)
 *   processConversion(op)
 *
 * Mamy jedną closure, która sama wybiera właściwą ścieżkę:
 *   operations.each(processOperation)
 */
@Slf4j
@Component
class OperationTypeDispatcher {

    // --- CLOSURE DISPATCHER (serce tego wzorca) ---
    // Każda operacja trafia tu i dostaje właściwe przetworzenie
    def processOperation = { OperationDto op ->
        log.info("Dispatch: {} [{}] kwota={} {}", op.operationType, op.operationId, op.amount, op.sourceCurrency)

        switch (op.operationType) {
            case "DEPOSIT":
                return processDeposit(op)
            case "WITHDRAWAL":
                return processWithdrawal(op)
            case "TRANSFER":
                return processTransfer(op)
            case "CONVERSION":
                return processConversion(op)
            default:
                log.warn("Nieznany typ operacji: {} dla ID: {}", op.operationType, op.operationId)
                return [success: false, reason: "UNKNOWN_TYPE"]
        }
    }

    // --- Wywołanie zbiorcze przez each ---
    // To jest dokładnie wzorzec z LAB101: scenarios.each(registerScenario)
    List<Map> processBatch(List<OperationDto> operations) {
        log.info("Przetwarzanie paczki {} operacji", operations.size())
        return operations.collect { op -> processOperation(op) }
    }

    // --- Logika każdego typu ---

    private Map processDeposit(OperationDto op) {
        log.info("DEPOSIT: konto={}, kwota={} {}", op.targetAccount, op.amount, op.sourceCurrency)
        // Tu docelowo: walidacja, zapis, powiadomienie
        [success: true, type: "DEPOSIT", operationId: op.operationId]
    }

    private Map processWithdrawal(OperationDto op) {
        log.info("WITHDRAWAL: konto={}, kwota={} {}", op.sourceAccount, op.amount, op.sourceCurrency)
        [success: true, type: "WITHDRAWAL", operationId: op.operationId]
    }

    private Map processTransfer(OperationDto op) {
        log.info("TRANSFER: z={} na={}, kwota={} {}", op.sourceAccount, op.targetAccount, op.amount, op.sourceCurrency)
        [success: true, type: "TRANSFER", operationId: op.operationId]
    }

    private Map processConversion(OperationDto op) {
        log.info("CONVERSION: {} {} -> {} kurs={}", op.amount, op.sourceCurrency, op.targetCurrency, op.fxRate)
        [success: true, type: "CONVERSION", operationId: op.operationId]
    }
}