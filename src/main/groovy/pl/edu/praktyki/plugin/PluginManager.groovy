package pl.edu.praktyki.plugin

/**
 * Manager, który przechowuje i wykonuje listę wtyczek.
 * Każda wtyczka to domknięcie (Closure), które przyjmuje dane (np. Transaction).
 */
class PluginManager {
    private List<Closure> plugins = []

    // Dodawanie nowej logiki w czasie działania programu
    void addPlugin(Closure plugin) {
        plugins << plugin
    }

    // Wykonanie wszystkich wtyczek na raz
    void runAll(Object data) {
        plugins.each { plugin ->
            try {
                plugin(data)
            } catch (Exception e) {
                println "[PLUGIN ERROR] Wtyczka zawiodła: ${e.message}"
            }
        }
    }
}