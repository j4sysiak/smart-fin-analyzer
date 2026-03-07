package pl.edu.praktyki.dynamic

class DynamicDataContainer {
    // Nasz wewnętrzny magazyn danych
    private Map<String, Object> data = [:]

    // 1. metoda methodMissing: Przechwytuje wywołania nieistniejących metod
    def methodMissing(String name, args) {
        println ">>> Wykryto wywołanie nieistniejącej metody: $name z argumentami $args"

        // Jeśli metoda zaczyna się od "set", zapisujemy wartość
        if (name.startsWith("set")) {
            String property = name.substring(3).toLowerCase()
            data[property] = args[0]
            return "Zapisano $property = ${args[0]}"
        }

        // Jeśli metoda zaczyna się od "get", odczytujemy wartość
        if (name.startsWith("get")) {
            String property = name.substring(3).toLowerCase()
            return data[property]
        }

        // Obsługa wyzwania: Dynamiczna suma
        if (name == "calculateSum") {
            // args to tablica argumentów przekazanych do metody (np. [1, 2, 3])
            // .sum() to metoda Groovy'ego na kolekcjach
            return args.toList().sum()
        }

        throw new MissingMethodException(name, DynamicDataContainer, args)
    }

    // 2. metoda propertyMissing: Przechwytuje odwołania do nieistniejących pól
    def propertyMissing(String name) {
        return data[name]
    }
}