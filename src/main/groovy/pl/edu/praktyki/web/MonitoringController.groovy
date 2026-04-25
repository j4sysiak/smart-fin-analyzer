package pl.edu.praktyki.web

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import pl.edu.praktyki.service.EvilService
import pl.edu.praktyki.service.ThreadTracker

@RestController
@RequestMapping("/api/admin/monitoring")
class MonitoringController {

    @Autowired
    ThreadTracker threadTracker

    @Autowired EvilService evilService // Wstrzyknij zły serwis

    @GetMapping("/threads")
    @PreAuthorize("hasRole('ADMIN')") // Bezpieczeństwo przede wszystkim!
    Map<String, Object> getAsyncThreadStats() {
        // Zwracamy zrzut pamięci trackera
        return [
                systemTime: java.time.LocalDateTime.now().toString(),
                activeTasksInfo: threadTracker.snapshot()
        ]
    }

    @GetMapping("/trigger-error")
    @PreAuthorize("hasRole('ADMIN')")
    String triggerError() {
        evilService.throwErrorAsync() // To wywoła asynchroniczny wybuch
        return "Zlecono wybuch w tle. Sprawdź /threads za chwilę."
    }
}