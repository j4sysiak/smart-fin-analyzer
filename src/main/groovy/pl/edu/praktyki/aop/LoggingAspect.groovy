package pl.edu.praktyki.aop

import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation.Around
import org.aspectj.lang.annotation.Aspect
import org.springframework.stereotype.Component

@Aspect
@Component
class LoggingAspect {

    /*
Ten kod to **aspekt AOP (Aspect-Oriented Programming)**, który automatycznie mierzy czas wykonania metod.
Oto jak działa krok po kroku:

### 1. `@Around` + Pointcut (linia 15)
```groovy
@Around("execution(* pl.edu.praktyki.service.*.*(..))")
```
Mówi Springowi: **"Przechwyć każde wywołanie dowolnej metody"** w pakiecie `pl.edu.praktyki.service`.
Czyli np. jeśli masz `OrderService.processOrders()`, to ta metoda zostanie "opakowana" przez aspekt.

### 2. Pomiar czasu (linie 17–22)
- **Przed** wywołaniem oryginalnej metody → zapisz czas startu (`start`)
- `joinPoint.proceed()` → **wywołaj oryginalną metodę** (np. `processOrders()`)
- **Po** wywołaniu → oblicz różnicę czasu (`executionTime`)

### 3. Logowanie (linie 24–25)
Wypisuje na konsolę ile milisekund trwało wykonanie metody, np.:
```
>>> [AOP PROFILER] Metoda processOrders wykonała się w 142ms
```

### 4. `return proceed` (linia 27)
Zwraca wynik oryginalnej metody do wywołującego kodu — aspekt jest **przezroczysty**, nie zmienia wyniku.

---

### Podsumowanie
To jest **profiler** — automatycznie mierzy czas wszystkich metod serwisowych **bez modyfikowania ich kodu**.
To główna zaleta AOP: dodajesz funkcjonalność przekrojową (logowanie, bezpieczeństwo, transakcje) bez ingerencji w logikę biznesową.

     */

    // "Around" oznacza: wykonaj to przed i po metodzie
    // @Around("execution(* pl.edu.praktyki.service.*.*(..))")
    // ^ Wyrażenie "Pointcut": celuj we wszystkie metody w pakiecie .service
    @Around("execution(* pl.edu.praktyki.service.*.*(..))")
    Object logExecutionTime(ProceedingJoinPoint joinPoint) throws Throwable {
        long start = System.currentTimeMillis()

        // Tutaj wołamy oryginalną metodę (np. processOrders)
        Object proceed = joinPoint.proceed()

        long executionTime = System.currentTimeMillis() - start

        println ">>> [AOP PROFILER] Metoda ${joinPoint.signature.name} " +
                "wykonała się w ${executionTime}ms"

        return proceed
    }
}