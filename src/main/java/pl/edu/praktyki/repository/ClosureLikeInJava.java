package pl.edu.praktyki.repository;

import java.util.concurrent.atomic.AtomicInteger;

public class ClosureLikeInJava {
    public static void main(String[] args) {
        AtomicInteger calls = new AtomicInteger(0);

        // odpowiednik: def execute = { action -> action() }
        java.util.function.Consumer<Runnable> execute = action -> action.run();

        // odpowiednik: execute { calls++ }
        execute.accept(() -> calls.incrementAndGet());

        System.out.println(calls.get()); // 1

    }

    public static void execute(Runnable action) {
        action.run();  // uruchamiam to co dostałem
    }
}
