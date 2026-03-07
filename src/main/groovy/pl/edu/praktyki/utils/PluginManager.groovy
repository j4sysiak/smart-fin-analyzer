package pl.edu.praktyki.utils

class PluginManager {
    // Lista domknięć (pluginów)
    private List<Closure> plugins = []

    void addPlugin(Closure plugin) {
        plugins << plugin
    }

    void runAll(Object data) {
        plugins.each { it(data) }
    }
}
