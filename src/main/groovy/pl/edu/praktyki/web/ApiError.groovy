package pl.edu.praktyki.web

import java.time.LocalDateTime

class ApiError {
    int status
    String message
    LocalDateTime timestamp = LocalDateTime.now()
}