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
 