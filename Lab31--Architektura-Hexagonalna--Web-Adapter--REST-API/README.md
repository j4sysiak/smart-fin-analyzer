Lab 31
------

Lab 31: Architektura Hexagonalna – Web Adapter (REST API)
---------------------------------------------------------

Cel: 
To jest wielki krok architektoniczny. 
Obecnie Twoja aplikacja jest sterowana przez CLI (CommandLineRunner). 
Dodamy do niej Drugie Wejście – przez przeglądarkę/HTTP.

Dzięki temu ta sama logika biznesowa (Ingester, Analytics) będzie mogła być wywoływana z konsoli ORAZ przez Internet.

Krok 31.1: Dodanie zależności Web
---------------------------------

W build.gradle dodaj:

implementation 'org.springframework.boot:spring-boot-starter-web'


Krok 31.2: Stworzenie Kontrolera (TransactionController.groovy)
---------------------------------------------------------------

Stwórz `src/main/groovy/pl/edu/praktyki/web/TransactionController.groovy`.

 
Dodaj Web Starter (Lab 31): To sprawi, że Twoja aplikacja po uruchomieniu nie zamknie się od razu, 
ale będzie wisieć i nasłuchiwać na porcie 8080.




To jest kluczowy moment transformacji. 
Twoja aplikacja przestała być tylko skryptem (który robi robotę i znika), a stała się serwerem (który działa ciągle i nasłuchuje zapytań).
Aby to dokładnie przetestować, musimy podejść do tematu na dwa sposoby:

1. Manualnie (przez przeglądarkę/curl) – żeby zobaczyć "na żywo".
2. Automatycznie (Spock + MockMvc) – to jest profesjonalny standard testowania kontrolerów w Springu.


Krok 2: Test Manualny (Uruchomienie Serwera)
--------------------------------------------
Teraz, gdy dodałeś spring-boot-starter-web, Twoja aplikacja po wykonaniu run() w CommandLineRunner nie wyłączy się. Będzie czekać na żądania HTTP.

./gradlew runSmartFinDb -PappArgs="-u Tester"

Poczekaj, aż w konsoli zobaczysz logi importu, a na końcu raport. Aplikacja nie wróci do znaku zachęty w terminalu. To dobrze! Działa serwer Tomcat na porcie 8080.
Otwórz przeglądarkę i wejdź pod adresy:
http://localhost:8080/api/transactions
(Powinieneś zobaczyć JSON z listą transakcji, które CLI zaimportowało na starcie)
http://localhost:8080/api/transactions/stats
(Powinieneś zobaczyć JSON ze statystykami, np. {"balance": 2243.09, "topCategory": "Rozrywka"})
Aby zamknąć aplikację w terminalu, naciśnij Ctrl + C.


Krok 3: Test Automatyczny w Spocku (MockMvc)
--------------------------------------------

To jest "Crème de la crème" testowania w Springu. 
Nie chcemy za każdym razem klikać w przeglądarce. 
Użyjemy narzędzia MockMvc, które udaje, że wysyła zapytanie HTTP do Twojego kontrolera, ale robi to w pamięci (bardzo szybko).
Stwórz plik `src/test/groovy/pl/edu/praktyki/web/TransactionControllerSpec.groovy`:

Co tu jest ważne? (Dla rekrutera)

@AutoConfigureMockMvc: 
To standard testowania Web API w Spring Boot. 
Nie stawiamy prawdziwego serwera na porcie (co jest wolne), tylko symulujemy warstwę HTTP.

jsonPath: 
To język zapytań do JSON-a. $[0].category oznacza "weź pierwszy element z listy i daj jego pole category". 
Spock świetnie się z tym integruje.

Izolacja danych (setup): 
Dzięki czyszczeniu repozytorium przed testem, mamy pewność, że dane z CommandLineRunner (który uruchamia się przy starcie kontekstu) 
nie zakłócą nam wyników testu.