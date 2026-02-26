Lab 33
------

Lab 33: Dokumentacja API na żywo (Swagger / OpenAPI)
----------------------------------------------------

Cel: 
Automatyczne wygenerowanie pięknego, interaktywnego panelu (strony WWW), z którego można testować Twoje API (klikać GET, POST) bez używania zewnętrznych programów jak Postman.

Krok 33.1: Dodanie zależności
Dla Spring Boot 3 używamy biblioteki springdoc. Otwórz build.gradle i dodaj w dependencies:
```groovy
// Swagger / OpenAPI UI dla Spring Boot 3
    implementation 'org.springdoc:springdoc-openapi-starter-webmvc-ui:2.3.0'
```

Krok 33.2: Uruchomienie i test
------------------------------

Nie musisz pisać ani jednej linijki kodu, aby to zadziałało. 
Spring Boot sam przeskanuje Twoje kontrolery.
Uruchom aplikację:

./gradlew runSmartFinDb -PappArgs="-u Jacek -c EUR"

Otwórz przeglądarkę i wejdź pod adres:
👉 http://localhost:8080/swagger-ui/index.html

Efekt: 
Zobaczysz graficzny interfejs. 
Rozwiń transaction-controller, kliknij /api/transactions -> Try it out -> Execute. 
Zobaczysz swoje dane z bazy od razu na ekranie!












