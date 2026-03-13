package pl.edu.praktyki.aop

import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation.Around
import org.aspectj.lang.annotation.Aspect
import org.springframework.stereotype.Component
import pl.edu.praktyki.domain.Transaction

@Aspect
@Component
class SecurityAspect {

    @Around("execution(* pl.edu.praktyki.service.*.ingestTransactions(..))")
    Object checkTransactionAmount(ProceedingJoinPoint joinPoint) throws Throwable {

        // joinPoint.args zawiera argumenty przekazane do metody (tu: List<Transaction>)
        def args = joinPoint.args

        if (args && args[0] instanceof List) {
            List<Transaction> transactions = args[0]

            // Logika bezpieczeństwa: sprawdzamy każdą transakcję
            transactions.each { tx ->
                if (tx.amount > 100_000) {
                    throw new SecurityException("ALERT: Nieautoryzowana wysoka kwota! Transakcja: ${tx.id}")
                }
            }
        }

        // Jeśli wszystko OK, pozwalamy metodzie działać dalej
        return joinPoint.proceed()
    }
}