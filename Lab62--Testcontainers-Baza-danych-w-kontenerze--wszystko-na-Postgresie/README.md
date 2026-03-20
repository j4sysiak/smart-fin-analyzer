Lab62
-----

Lab62--Testcontainers-Baza-danych-w-kontenerze--wszystko-na-Postgresie
----------------------------------------------------------------------

moje pytanie:
-------------
Ja bym chcial, zeby wszystkie testy chodzily na tylko Postgresie.   
Bo po co mi tylko ten jeden test `IntegrationDbSpec` jako integracyjny test `Testcontainers`
 - on sluzy tylko do tego, czy uruchamia mi sie Postgres na Dockerze i zapisuje encje TransactionEntity - i to wszystko.

Odpowiedź Mentora:
------------------
To jest bardzo ważne pytanie architektoniczne, które w wielu zespołach wywołuje gorące dyskusje.
Twoja intuicja jest słuszna: "Po co mi dwa rodzaje bazy?" Postgres i H2.
W idealnym świecie chcesz testować dokładnie na tym samym, na czym aplikacja działa na produkcji.

Jeśli używasz `Testcontainers`, masz dwie drogi, które całkowicie eliminują potrzebę posiadania `H2` w testach.

1. Opcja "All-In-One" (`Testcontainers` dla WSZYSTKICH testów)
Jeśli chcesz, żeby każdy test integracyjny korzystał z prawdziwego Postgresa, 
musisz usunąć konfigurację `H2` z `src/test/resources/application.properties` i wymusić użycie `Testcontainers` w całej klasie bazowej testów.

Najlepszym sposobem jest stworzenie `Klasy Bazowej Testów` (`BaseIntegrationSpec`), po której będą dziedziczyć wszystkie Twoje testy integracyjne.
Teraz wystarczy, że Twoje klasy testowe (np. OrderIntegrationSpec, TransactionControllerSpec) będą rozszerzać BaseIntegrationSpec zamiast Specification.

2. Dlaczego programiści czasem jednak zostawiają H2? (Dylemat Mida)
Jeśli przejdziesz w 100% na Testcontainers (Opcja 1), zyskasz "święty spokój" – zero rozbieżności między bazami. 
Dlaczego więc niektórzy się opierają?

Szybkość: 
Testcontainers zawsze zajmie te dodatkowe 5-10 sekund na start kontenera. 
Jeśli masz 500 testów integracyjnych, `H2` przetestuje je w 10 sekund, a Testcontainers w 3 minuty.

Wymagania infrastruktury: 
`Testcontainers` wymaga `Dockera` na maszynie, na której uruchamiasz testy (np. w Jenkinsie, na laptopie kolegi). 
H2 zadziała wszędzie "z palca".

Moja rekomendacja jako Mentora:

Do portfolio: 
Przejdź na Opcję 1 (tylko PostgreSQL). 
Dlaczego? 
Bo to pokazuje, że nie boisz się Dockera i rozumiesz, że H2 to tylko "zabawka" do testów, a prawdziwa baza to Postgres. 
To wygląda o wiele lepiej w CV.

Architektoniczny sznyt: 
Użycie `BaseIntegrationSpec` pokazuje, że potrafisz zarządzać cyklem życia infrastruktury w kodzie (tzw. Test Infrastructure as Code).

Startuj z Testcontainers tylko z bazą PostgreSQL, a nie z H2.:
--------------------------------------------------------------
Wyrzucamy "zabawkowe" H2 i przechodzimy na 100% PostgreSQL w testach, zachowując przy tym maksymalną wydajność.
Aby nie zabijać czasu budowania (startowanie nowego kontenera dla każdej klasy testowej trwałoby wieki),
zastosujemy wzorzec `Singleton Container`. 
Kontener uruchomi się tylko raz na początku całego cyklu testów i będzie współdzielony przez wszystkie klasy testowe.

Oto jak to wdrożyć w 3 krokach.

Krok 1: Stworzenie Klasy Bazowej (BaseIntegrationSpec)
------------------------------------------------------

Stwórz nowy plik `src/test/groovy/pl/edu/praktyki/BaseIntegrationSpec.groovy`.
Ta klasa będzie fundamentem dla każdego testu integracyjnego w Twoim projekcie.

```groovy
package pl.edu.praktyki

import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer
import spock.lang.Specification

// 1. Główne adnotacje Spring Boota - definiujemy raz!
@SpringBootTest(classes = [SmartFinDbApp])
@ActiveProfiles("test")
abstract class BaseIntegrationSpec extends Specification {

    // 2. Statyczny kontener - uruchomi się TYLKO RAZ dla całego zestawu testów
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")

    // Blok statyczny uruchamia się w momencie załadowania klasy przez JVM
    static {
        postgres.start()
    }

    // 3. Dynamiczne wstrzyknięcie danych logowania do Springa
    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", { postgres.getJdbcUrl() })
        registry.add("spring.datasource.username", { postgres.getUsername() })
        registry.add("spring.datasource.password", { postgres.getPassword() })
        
        // Zmuszamy Hibernate do stworzenia tabel od zera w pustym Postgresie
        registry.add("spring.jpa.hibernate.ddl-auto", { "create-drop" })
        registry.add("spring.flyway.enabled", { "false" })
    }
}
```

Krok 2: Refaktoryzacja Twoich Testów Integracyjnych
---------------------------------------------------
Teraz dzieje się najpiękniejsza rzecz z punktu widzenia Czystego Kodu (`Clean Code`). 
Twoje testy integracyjne pozbywają się tego całego "śmietnika" z adnotacjami na górze.

Otwórz np. `src/test/groovy/pl/edu/praktyki/web/TransactionControllerSpec.groovy` i zobacz, 
jak cudownie się "odchudza":

```groovy
package pl.edu.praktyki.web

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import pl.edu.praktyki.BaseIntegrationSpec // <-- IMPORT BAZY
import pl.edu.praktyki.repository.TransactionRepository
import pl.edu.praktyki.repository.TransactionEntity

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

// USUŃ @SpringBootTest, @ActiveProfiles, @ContextConfiguration
// Zostaw tylko @AutoConfigureMockMvc (bo jest specyficzne dla tego testu)

@AutoConfigureMockMvc
class TransactionControllerSpec extends BaseIntegrationSpec { // <-- DZIEDZICZYMY!

    @Autowired MockMvc mvc
    @Autowired TransactionRepository repository

    def setup() {
        repository.deleteAll()
        // ... reszta kodu bez zmian ...
    }
    
    // ... testy bez zmian ...
}

```

Zrób dokładnie to samo dla innych testów integracyjnych (np. SmartFinIntegrationSpec czy IntegrationDbSpec, jeśli go zostawiasz):

Usuń @SpringBootTest, @ActiveProfiles, @ContextConfiguration, @TestPropertySource.

Zmień extends Specification na extends BaseIntegrationSpec.

Krok 3: Sprzątanie pliku konfiguracyjnego
-----------------------------------------

Skoro URL bazy i dane logowania "wstrzykujemy" w locie z klasy BaseIntegrationSpec, 
plik application.properties w testach może być teraz wręcz pusty!

Otwórz `src/test/resources/application.properties` i zostaw w nim tylko to, co ewentualnie nie dotyczy bazy danych:

```text
# Wypisywanie zapytań SQL dla łatwiejszego debugowania testów
spring.jpa.show-sql=true
spring.main.allow-bean-definition-overriding=true
```


(Wszystkie wpisy jdbc:tc:... i h2:mem poszły do kosza!)

Co właśnie osiągnąłeś? (Senior Level Architecture)

Jeden punkt prawdy (Single Source of Truth): Jeśli w przyszłości zechcesz dodać do testów kontener np. z Redisem albo Kafką, dopiszesz go w jednym pliku BaseIntegrationSpec. Wszystkie testy automatycznie zyskają do niego dostęp.

Prędkość: Testcontainers uruchomi postgres:16-alpine na początku, Spock przeleci przez wszystkie testy korzystając z tego samego kontenera, a na koniec go zabije. Testy będą trwały sekundy, a nie minuty.

Brak "Magii": Konfiguracja JDBC url w stylu jdbc:tc:... bywała kapryśna i potrafiła zepsuć wtyczkę Flyway'a czy Hibernate'a. Programowe wyciąganie postgres.getJdbcUrl() jest bezawaryjne.

Odpal teraz z terminala ./gradlew clean test. Zobaczysz w logach start kontenera PostgreSQL, a potem całą lawinę zielonych testów, które wykonują prawdziwe zapytania SQL na prawdziwym silniku!

To jest absolutny "Endgame" testowania w Spring Boot! Daj znać, jak poszło! 🚀🐳






