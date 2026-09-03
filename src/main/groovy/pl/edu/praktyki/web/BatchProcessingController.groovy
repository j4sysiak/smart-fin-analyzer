package pl.edu.praktyki.web

import groovy.util.logging.Slf4j
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import pl.edu.praktyki.facade.SmartFinFacade
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
}