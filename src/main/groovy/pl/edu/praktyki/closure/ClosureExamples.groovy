package pl.edu.praktyki.closure

class ClosureExamples {

    // odpowiednik: def square = { x -> x * x }
    int square(int x) {
        x * x
    }

    // odpowiednik: numbers.collect { it * 2 }
    List<Integer> doubleElements(List<Integer> numbers) {
        numbers.collect { it * 2 }
    }

    // odpowiednik: def isAdult = { age -> age >= 18 }
    boolean isAdult(int age) {
        age >= 18
    }

    // odpowiednik: def multiply = { x -> x * multiplier }
    // multiplier pochodzi "z zewnątrz" — tu przekazujemy go jako argument
    int multiply(int x, int multiplier) {
        x * multiplier
    }

    // odpowiednik: def repeatTwice = { action -> action(); action() }
    void repeatTwice(Closure action) {
        action()
        action()
    }
}

