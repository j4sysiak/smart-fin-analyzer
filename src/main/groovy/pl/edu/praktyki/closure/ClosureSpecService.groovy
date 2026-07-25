package pl.edu.praktyki.closure

class ClosureSpecService {

    String greet(String name) {
        "Cześć, $name"
    }

    String hello() {
        "Hej!"
    }

    int add(int a, int b) {
        a + b
    }

    List<String> greetAll(List<String> names) {
        def greetings = []
        names.each { name ->
            greetings << "Witaj $name"
        }
        greetings
    }

    String greetWithPrefix(String prefix, String name) {
        "$prefix $name"
    }

    Closure<Integer> multiplier(int factor) {
        { int value ->
            value * factor
        }
    }
}

