package pl.edu.praktyki.singleton

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import pl.edu.praktyki.repository.Counter
import pl.edu.praktyki.repository.CounterRepository

@Service
class TransactionCounterService {

    private final CounterRepository counterRepository

    TransactionCounterService(CounterRepository counterRepository) {
        this.counterRepository = counterRepository
    }

// Adnotacje takie jak @Async czy @Transactional tworzą "opakowanie" wokół Twojej klasy.
// Chodzi o klasę będącą beanem Springa — czyli klasę zarządzaną przez kontener
// (np. oznaczoną @Component, @Service, @Repository, @Configuration albo zdefiniowaną jako @Bean).
// Adnotacje takie jak @Async czy @Transactional działają przez utworzenie proxy wokół tego beana
// i przechwytywanie wywołań metod przychodzących z zewnątrz.

    @Transactional
    void increment(String name) {
        counterRepository.incrementByName(name)
    }

    int getCounter(String name) {
        return counterRepository.findByName(name)
                .map(Counter::getValue)
                .orElse(0)
    }
}
