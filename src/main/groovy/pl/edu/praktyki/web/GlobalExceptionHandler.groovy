package pl.edu.praktyki.web

import org.springframework.web.bind.MethodArgumentNotValidException // <- dodaj import na górze
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.server.ResponseStatusException
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.core.AuthenticationException

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

    // Najpierw obsłużymy wyjątki związane z bezpieczeństwem, żeby zwracać właściwe kody HTTP
    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ApiError> handleAccessDenied(AccessDeniedException ex) {
        def error = new ApiError(
                status: HttpStatus.FORBIDDEN.value(),
                message: "Access Denied"
        )
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error)
    }

    @ExceptionHandler(AuthenticationException.class)
    ResponseEntity<ApiError> handleAuthenticationException(AuthenticationException ex) {
        def error = new ApiError(
                status: HttpStatus.UNAUTHORIZED.value(),
                message: ex.message ?: "Unauthorized"
        )
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error)
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> handleGeneralException(Exception ex) {
        def error = new ApiError(
                status: HttpStatus.INTERNAL_SERVER_ERROR.value(),
                message: "Wewnętrzny błąd serwera: ${ex.message}"
        )
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error)
    }

    // NOWOŚĆ: Przechwytujemy błędy z @Valid z @PostMapping z transactionController::addTransaction
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> handleValidationException(MethodArgumentNotValidException ex) {

        // Zbieramy wszystkie błędy walidacji w jeden ładny tekst (Magia Groovy: collect + join)
        def errorsMessage = ex.bindingResult.fieldErrors
                .collect { "${it.field}: ${it.defaultMessage}" }
                .join(", ")

        def error = new ApiError(
                status: HttpStatus.BAD_REQUEST.value(),
                message: "Błąd walidacji danych: " + errorsMessage
        )

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error)
    }
}