package pl.edu.praktyki.web

import groovy.util.logging.Slf4j
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException
import pl.edu.praktyki.facade.SmartFinFacade
import pl.edu.praktyki.operation.OperationBatchAuditListener
import pl.edu.praktyki.web.dto.ProcessBatchRequest
import pl.edu.praktyki.web.dto.ProcessBatchResponse

import java.time.OffsetDateTime

@Slf4j
@RestController
@RequestMapping("/api/batches")
@Tag(name = "Batch Processing", description = "Uruchamianie wsadowego przetwarzania operacji")
class BatchProcessingController {

    private static final Set<String> ALLOWED_TYPES = [
            "DEPOSIT", "WITHDRAWAL", "TRANSFER", "CONVERSION"
    ] as Set<String>

    @Autowired
    SmartFinFacade facade

    @Autowired
    OperationBatchAuditListener operationBatchAuditListener

    @PostMapping("/process")
    @Operation(summary = "Uruchom przetwarzanie batcha",
               description = "Brak operationType = ALL, podany operationType = przetwarzanie jednego typu")
    ProcessBatchResponse processBatch(@RequestBody(required = false) ProcessBatchRequest request) {
        String rawType = request?.operationType?.trim()
        String normalizedType = rawType?.toUpperCase()

        if (normalizedType && !ALLOWED_TYPES.contains(normalizedType)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Nieprawidłowy operationType. Dozwolone: DEPOSIT, WITHDRAWAL, TRANSFER, CONVERSION"
            )
        }

        Map summary = facade.processOperationsBatch(normalizedType)
        String trigger = normalizedType ?: "ALL"

        return new ProcessBatchResponse(
                trigger: trigger,
                total: (summary.total ?: 0) as int,
                saved: (summary.saved ?: 0) as int,
                skipped: (summary.skipped ?: 0) as int,
                failed: (summary.failed ?: 0) as int,
                processedAt: OffsetDateTime.now()
        )
    }

    @GetMapping("/last-summary")
    @Operation(summary = "Pobierz ostatnie podsumowanie batcha",
            description = "Zwraca ostatni wynik przetwarzania zapisany przez listener eventu")
    ProcessBatchResponse lastSummary() {
        Map<String, Object> summary = operationBatchAuditListener.getLastSummary()
        if (!summary) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Brak ostatniego podsumowania batcha")
        }

        return new ProcessBatchResponse(
                trigger: summary.trigger as String,
                total: (summary.total ?: 0) as int,
                saved: (summary.saved ?: 0) as int,
                skipped: (summary.skipped ?: 0) as int,
                failed: (summary.failed ?: 0) as int,
                processedAt: summary.processedAt as OffsetDateTime
        )
    }

    @GetMapping("/history")
    @Operation(summary = "Pobierz historie podsumowan batcha",
            description = "Zwraca liste ostatnich podsumowan, opcjonalnie filtrowana po trigger")
    List<ProcessBatchResponse> history(
            @RequestParam(name = "limit", defaultValue = "20") Integer limit,
            @RequestParam(name = "trigger", required = false) String trigger) {
        int safeLimit = limit == null ? 20 : Math.max(1, Math.min(limit, 100))
        String normalizedTrigger = trigger?.trim()?.toUpperCase()

        List<Map<String, Object>> items = operationBatchAuditListener.getHistory(safeLimit, normalizedTrigger)

    /*
     items.collect { ... }
     { Map<String, Object> s -> ... }   <-- to anonimowa funkcja przekazywana do metody collect.

     Co robi items.collect ?
     collect iteruje po elementach listy i zwraca nową listę po przekształceniu każdego elementu.
     Co oznacza closure tutaj.

     Closure:
     - przyjmuje argument s
     - s ma typ Map<String, Object>
     - dla każdego elementu tworzy new ProcessBatchResponse(...)

     Efekt jest taki:
     - wejście: List<Map<String, Object>>
     - wyjście: List<ProcessBatchResponse>

    */

        return items.collect { Map<String, Object> s ->
            new ProcessBatchResponse(
                    trigger: s.trigger as String,
                    total: (s.total ?: 0) as int,
                    saved: (s.saved ?: 0) as int,
                    skipped: (s.skipped ?: 0) as int,
                    failed: (s.failed ?: 0) as int,
                    processedAt: s.processedAt as java.time.OffsetDateTime
            )
        }
    }
}