
Sposób 2: 
Prawdziwy Spring Boot (Wbudowana Konsola Web)

Skoro mamy już serwer WWW, Spring Boot posiada wbudowanego klienta bazy danych dostępnego 
przez przeglądarkę internetową! Nie potrzebujesz już w ogóle DBeavera.

Otwórz plik `src/main/resources/application.properties` i dodaj na końcu tę linijkę:


spring.h2.console.enabled=true

Uruchom aplikację (./gradlew runSmartFinDb -PappArgs="-u Jacek").

Otwórz przeglądarkę internetową i wejdź na adres:
👉 http://localhost:8080/h2-console

Zobaczysz panel logowania. 
Zwróć uwagę na pole JDBC URL. 
Upewnij się, że jest tam wpisane: `jdbc:h2:file:./db/smartfin`

Użytkownik to sa, pole hasła zostaw puste i kliknij Connect.
W przeglądarce otworzy Ci się pełnoprawny interfejs bazy danych!

Sposób 3: 
Odblokowanie współdzielenia (Magiczny parametr H2)

Jeśli koniecznie chcesz mieć włączoną aplikację i JEDNOCZEŚNIE podglądać dane w DBeaverze, 
musisz kazać bazie H2 wejść w tryb serwera automatycznego (AUTO_SERVER).

W pliku application.properties zmień URL bazy na taki (dodajemy parametr na końcu):

`spring.datasource.url=jdbc:h2:file:./db/smartfin;AUTO_SERVER=TRUE;DB_CLOSE_DELAY=-1`


W DBeaverze kliknij prawym przyciskiem na połączenie -> Edit Connection (Edytuj).

W URL bazy również dopisz ten parametr, aby wyglądał np. tak:
jdbc:h2:file:C:/dev/smart-fin-analyzer/db/smartfin;AUTO_SERVER=TRUE

Zrestartuj aplikację.

Teraz, gdy aplikacja Spring Boot utworzy bazę, 
automatycznie otworzy mały port w tle. 
Gdy DBeaver spróbuje się połączyć i zobaczy, że plik jest zablokowany, to "dogada się" z Twoją aplikacją przez ten port i współdzieli z nią bazę!

Zdecydowanie polecam Sposób 2 (Konsolę Webową). 
To jest standard w świecie Spring Boota. Daj znać, który sposób wybrałeś i czy widzisz swoje dane!