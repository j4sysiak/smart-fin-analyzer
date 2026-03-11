package pl.edu.praktyki.report.trait

import java.time.LocalTime
import java.time.format.DateTimeFormatter

trait AuditLog {
    List<String> history = []

    void audit(String message) {
        // Używamy nowoczesnego API zamiast przestarzałego Date.format()
        String time = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"))
        String entry = "[AUDIT] $time - $message"

        println entry
        history << entry
    }

    boolean hasAuditEntry(String fragment) {
        return history.any { it.contains(fragment) }
    }
}