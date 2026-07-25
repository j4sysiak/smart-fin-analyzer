package pl.edu.praktyki.repository;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class ClosureWithDataInJava {
    public static void main(String[] args) {
        AtomicReference<String> result = new AtomicReference<>();

        // odpowiednik: def greet = { name, a -> a(name) }
        BiConsumer<String, Consumer<String>> greet = (name, action) -> action.accept(name);

        greet.accept("Ania", person -> result.set("Cześć, " + person));

        System.out.println(result.get()); // Cześć, Ania
    }
}