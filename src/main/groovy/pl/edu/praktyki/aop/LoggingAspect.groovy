package pl.edu.praktyki.aop

import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation.Around
import org.aspectj.lang.annotation.Aspect
import org.springframework.stereotype.Component

@Aspect
@Component
class LoggingAspect {

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