package pl.edu.praktyki.web

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.server.ResponseStatusException

@ControllerAdvice
class GlobalExceptionHandler {

    // 1. Przechwytuje wyjątki ResponseStatusException (np. nasze 404 z kontrolera)
    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<ApiError> handleResponseStatusException(ResponseStatusException ex) {
        def error = new ApiError(
                status: ex.statusCode.value(),
                message: ex.reason ?: "Nieznany błąd biznesowy"
        )
        return ResponseEntity.status(ex.statusCode).body(error)
    }

    // 2. Przechwytuje wszystkie inne, nieobsłużone wyjątki Javy/Groovy (tzw. "łapacz wszystkiego")
    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> handleGeneralException(Exception ex) {
        def error = new ApiError(
                status: HttpStatus.INTERNAL_SERVER_ERROR.value(),
                message: "Wewnętrzny błąd serwera: ${ex.message}"
        )
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error)
    }
}