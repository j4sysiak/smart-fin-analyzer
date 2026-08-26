package pl.edu.praktyki.operation

import groovy.util.logging.Slf4j
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * Cel: orkiestracja całego flow: pobierz → zmapuj → dispatch → zapisz do DB.
 * Serwis orkiestrujący cały pipeline operacji wsadowych:
 *
 *   MockServer → BankOperationClient
 *       → List<OperationDto>
 *       → OperationTypeDispatcher (closure!)
 *       → OperationRepository (zapis do DB)
 */
@Slf4j
@Service
class BatchOperationService {

    final BankOperationClient bankOperationClient
    final OperationTypeDispatcher dispatcher
    final OperationRepository operationRepository

    BatchOperationService(BankOperationClient bankOperationClient,
                          OperationTypeDispatcher dispatcher,
                          OperationRepository operationRepository) {
        this.bankOperationClient  = bankOperationClient
        this.dispatcher           = dispatcher
        this.operationRepository  = operationRepository
    }

    /**
     * Pobiera wszystkie 4 typy operacji z MockServera i przetwarza je.
     * Zwraca podsumowanie: ile zapisano, ile pominięto (duplikaty), ile błędów.
     */
    @Transactional
    Map processAll() {
        log.info("=== Start przetwarzania wszystkich operacji wsadowych ===")

        def all = bankOperationClient.fetchAll()
        log.info("Pobrano {} operacji łącznie", all.size())

        return processList(all)
    }

    /**
     * Pobiera i przetwarza tylko jeden typ operacji.
     * Typ: DEPOSIT | WITHDRAWAL | TRANSFER | CONVERSION
     */
    @Transactional
    Map processType(String operationType) {
        log.info("=== Start przetwarzania operacji typu: {} ===", operationType)

        def ops = switch (operationType.toUpperCase()) {
            case "DEPOSIT"    -> bankOperationClient.fetchDeposits()
            case "WITHDRAWAL" -> bankOperationClient.fetchWithdrawals()
            case "TRANSFER"   -> bankOperationClient.fetchTransfers()
            case "CONVERSION" -> bankOperationClient.fetchConversions()
            default -> {
                log.warn("Nieznany typ operacji: {}", operationType)
                yield []
            }
        }

        return processList(ops)
    }

    /**
     * Przetwarza przekazaną listę OperationDto:
     * 1. Pomija duplikaty (idempotencja po operationId)
     * 2. Dispatcher wywołuje closure dla każdej operacji
     * 3. Zapisuje encję do bazy
     * 4. Zwraca podsumowanie
     */
    @Transactional
    Map processList(List<OperationDto> operations) {
        int saved    = 0
        int skipped  = 0
        int failed   = 0

        operations.each { OperationDto op ->
            try {
                // Idempotencja: nie przetwarzaj tej samej operacji dwa razy
                if (operationRepository.existsByOperationId(op.operationId)) {
                    log.debug("Pominięto duplikat: {}", op.operationId)
                    skipped++
                    return
                }

                // Dispatch przez closure
                def result = dispatcher.processOperation(op)

                // Zapis do bazy
                def entity = op.toEntity()
                entity.status = result.success ? "PROCESSED" : "FAILED"
                operationRepository.save(entity)

                if (result.success) saved++ else failed++

            } catch (Exception e) {
                log.error("Błąd przetwarzania operacji {}: {}", op.operationId, e.message)
                failed++
            }
        }

        def summary = [
                total  : operations.size(),
                saved  : saved,
                skipped: skipped,
                failed : failed
        ]
        log.info("=== Zakończono przetwarzanie: {} ===", summary)
        return summary
    }
}