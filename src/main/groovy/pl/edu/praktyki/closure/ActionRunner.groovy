package pl.edu.praktyki.closure

/**
 * Klasa produkcyjna odpowiadająca testom z ClosureAsMethodArgumentSpec.
 *
 * Test 1: execute(Closure action)
 *   - dostaje closure bez argumentów
 *   - po prostu ją uruchamia
 *   - odpowiednik: def execute = { action -> action() }
 *
 * Test 2: greet(String name, Closure action)
 *   - dostaje dane (imię) i closure
 *   - przekazuje dane do closure i ją uruchamia
 *   - odpowiednik: def greet = { name, a -> a(name) }
 */
class ActionRunner {

    /**
     * Uruchamia przekazaną akcję.
     *
     * Przykład użycia:
     *   runner.execute { println "hej" }
     */
    static void execute(Closure action) {
        action()   //  uruchamia to co dostał, czyli action, czyli to w { ... } czyli odpali się po prostu: `calls++`
    }

    /**
     * Przekazuje imię do akcji i ją uruchamia.
     *
     * Przykład użycia:
     *   runner.greet("Ania") { person -> println "Cześć, $person" }
     */
    static void greet(String name, Closure action) {
        action(name)  //  uruchamia to co dostał:  to w { ... }  odpali się:  ` person -> result = "Cześć, $person" `
                                                                                        // gdzie person = name a przyszło: "Ania"
    }
}