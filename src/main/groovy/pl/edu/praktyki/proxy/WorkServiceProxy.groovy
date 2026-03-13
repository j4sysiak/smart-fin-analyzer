package pl.edu.praktyki.proxy

class WorkServiceProxy implements WorkService {

    private final WorkService realService // To jest nasz "Dyrektor"

    WorkServiceProxy(WorkService realService) {
        this.realService = realService
    }

    @Override
    void doHeavyWork() {
        // --- LOGIKA ASYSTENTA (Proxy) ---
        println ">>> [Asystent] Zapisuję czas rozpoczęcia..."
        long start = System.currentTimeMillis()

        // Wywołanie Dyrektora
        realService.doHeavyWork()

        // --- LOGIKA ASYSTENTA (Proxy) ---
        long end = System.currentTimeMillis()
        println ">>> [Asystent] Dyrektor skończył po ${end - start}ms"
    }
}
