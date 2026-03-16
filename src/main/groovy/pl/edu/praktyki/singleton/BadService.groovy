package pl.edu.praktyki.singleton

import org.springframework.stereotype.Service

@Service
class BadService {
// NIEBEZPIECZEŃSTWO! To pole `counter` współdzielą WSZYSCY użytkownicy tego serwisu, a jego modyfikacja nie jest synchronizowana.
// To oznacza, że jeśli wielu użytkowników będzie korzystać z tego serwisu jednocześnie np poprzez REST albo
// jakieś wywolanie wielowatkowe (look at: SingletonSpec), mogą wystąpić błędy związane z wyścigiem danych
    private int counter = 0

    void increment() {
        counter++
    }

    int getCounter() { return counter }
}