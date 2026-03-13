package pl.edu.praktyki.proxy

class RealWorkService implements WorkService {
    @Override
    void doHeavyWork() {
        println ">>> [Dyrektor] Wykonuję ciężką pracę..."
        Thread.sleep(1000) // Symulacja czasu
    }
}
