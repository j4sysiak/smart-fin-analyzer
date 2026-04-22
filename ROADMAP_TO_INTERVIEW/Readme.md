To jest dokładnie to nastawienie, 
którego szukają firmy u kandydatów na pozycję Mid / Regular! 
Koniec z zabawą w "odizolowane przykłady". 
Zmieniamy tryb na "Enterprise Hardcore".

Przez najbliższe tygodnie będziemy ten projekt "piłować", optymalizować i łamać, żebyś na rozmowie kwalifikacyjnej o każdej technologii mógł powiedzieć: 
"Tak, miałem z tym problem na produkcji mojego projektu i rozwiązałem to w ten sposób...".

Oto Twój 3-miesięczny Master-Plan (Roadmapa na Mida). 
----------------------------------------------------

🗺️ Mapa Drogowa: "Droga do Mida" (Smart-Fin-Analyzer)


🛡️ Etap 1: Kuloodporne Testy i Izolacja - Lab59
--------------------------------------------------------------------

Prawdziwy system nie może polegać na zewnętrznym internecie w testach.

WireMock:  (symulacja odleglego servera www)
--------------------------------------------
Zamockujemy zewnętrzne API walutowe, żeby testy przechodziły nawet w samolocie bez WiFi.

Testcontainers: 
----------------
Wyrzucimy bazę H2 z testów integracyjnych i odpalimy prawdziwego PostgreSQL'a w kontenerze Dockerowym 
z poziomu testu w Spocku.

--------- app: SmartFinDbApp
1. uruchomienie aplikacji - OK
2. baza H2 tylko dla aplikacji (nie testów) - OK
3. REST - OK
4. Swagger - OK
3. Postman - OK
4. H2 konsola - OK
   --------  testy Spec
2. ./gradlew clean test - OK
5. testy dla Postgres profil: local-pg - OK
5. testy dla Postgres profil: tc - OK

🔒 Etap 2: Spring Security i JWT (Wymóg rynkowy nr 1)
-----------------------------------------------------

API bez zabezpieczeń to darmowe zaproszenie dla hakerów.

Dodamy logowanie i autoryzację.

Wygenerujemy tokeny JWT (JSON Web Token).

Napiszemy testy w Spocku, które sprawdzają zabezpieczenia (Role-Based Access Control).


🚀 Etap 3: Big Data i Optymalizacja Hibernate
---------------------------------------------

Twój system działa dla 10 transakcji. Co, jeśli wgramy plik z 1,000,000 transakcji?

Problem OutOfMemoryError i jak go unikać.

Batch processing w Hibernate (zapisywanie po 50000 rekordów naraz, a nie po jednym).

Paginacja (Stronicowanie) w `REST API` – jak zwracać dane partiami, żeby nie zabić serwera.


🔄 Etap 4: Zaawansowana Asynchroniczność i Eventy
-------------------------------------------------
Rozbicie monolitu.
Spring `@Async` vs `GPars`.
Pule wątków `Thread Pools` – jak je konfigurować, żeby nie zabić serwera.
Architektura Event-Driven (`CQRS` na wesoło) – rzucanie asynchronicznych zdarzeń w Spring.


Etap 5: Audyt i Śledzenie Zmian (JPA Auditing)
--------------------------------------------
Automatyczne zapisywanie informacji o tym, kto i kiedy stworzył lub zmodyfikował rekord.


Etap 6: Zaawansowane Modelowanie i Relacje (JPA Relationships).
---------------------------------------------------------------
W świecie Mid-level, dane powinny mieć swoją strukturę i relacje.


Etap 7: API Documentation & Contracts (Swagger, JSON Schemas)
-------------------------------------------------------------


Etap 8: Konteneryzacja i Docker Compose, żeby Twój Postgres i aplikacja startowały jednym kliknięciem
-----------------------------------------------------------------------------------------------------