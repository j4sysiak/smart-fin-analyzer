Lab 29
------

Lab 29: Profesjonalne Logowanie (Slf4j + Logback)
-------------------------------------------------

Cel: 
Wyeliminować wszystkie println, zastąpić je loggerem, skonfigurować format logów i zapis do pliku.
Dlaczego? println spowalnia aplikację i nie daje kontroli. 
@Slf4j w Groovy to standard.

Krok 29.1: Konfiguracja Logback (logback.xml)
---------------------------------------------

Spring Boot używa domyślnie Logbacka. 
Musimy go skonfigurować, żeby pisał do pliku i kolorował konsolę.
Stwórz plik `src/main/resources/logback.xml`:

Krok 29.2: Refaktoryzacja kodu (Usuwamy println)
------------------------------------------------

Groovy ma świetną adnotację @Slf4j, która automatycznie dodaje zmienną log do klasy.
Zaktualizuj `src/main/groovy/pl/edu/praktyki/service/CurrencyService.groovy`:

Zadanie: Zrób to samo dla `TransactionIngesterService` (zamień println na log.info lub log.debug).
