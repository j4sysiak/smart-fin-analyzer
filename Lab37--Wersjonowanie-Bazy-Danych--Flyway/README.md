Lab 37
------

Mamy w kodzie jeden, bardzo niebezpieczny anty-wzorzec (anti-pattern), za który na produkcji można stracić pracę.
W pliku `application.properties` mamy wpis: `spring.jpa.hibernate.ddl-auto=update`. 
Oznacza to, że pozwalamy Hibernate'owi (Springowi) na automatyczne modyfikowanie struktury tabel w bazie danych. 
Na produkcji to proszenie się o kłopoty (np. przypadkowe usunięcie kolumny z milionem rekordów).

Wchodzimy w Fazę 10: Enterprise Data Management.

Lab 37: Wersjonowanie Bazy Danych (Flyway)
------------------------------------------

Cel: 
Przejęcie kontroli nad strukturą bazy danych za pomocą narzędzia `Flyway`. 
Zamiast pozwalać Springowi zgadywać, jak ma wyglądać tabela, napiszemy dokładny skrypt migracyjny (tzw. "Git dla bazy danych").

Krok 37.1: Dodanie zależności Flyway
------------------------------------

Otwórz `build.gradle` i dodaj do sekcji dependencies bibliotekę `Flyway` (wymaganą w projektach Spring Boot).

// Flyway - zarządzanie migracjami bazy danych
implementation 'org.flywaydb:flyway-core'

Krok 37.2: Zabezpieczenie application.properties
------------------------------------------------

Zabieramy Hibernate'owi prawa do tworzenia tabel.
Otwórz `src/main/resources/application.properties` i zmień wartość `ddl-auto` na `validate`
(Hibernate będzie tylko sprawdzał, czy tabele są poprawne, ale sam ich nie dotknie).

# ZMIENIAMY Z update NA validate!
spring.jpa.hibernate.ddl-auto=validate

# Włączamy Flyway
spring.flyway.enabled=true


Krok 37.3: Usunięcie starej bazy
--------------------------------

Ponieważ wchodzimy w nowy system zarządzania bazą, najlepiej zacząć z czystą kartą.
W swoim projekcie usuń całkowicie folder db (ten, w którym leży plik smartfin.mv.db).

Krok 37.4: Tworzenie skryptu migracyjnego
-----------------------------------------

Flyway wymaga ścisłej konwencji nazewnictwa. 
Skrypty muszą leżeć w folderze `src/main/resources/db/migration/` 
i nazywać się np. V1__nazwa.sql (uwaga: dwa podkreślniki po numerze wersji!).

1. Stwórz katalogi: `src/main/resources/db/migration/`
2. Stwórz w nim plik: V1__init_schema.sql
3. Wklej do niego kod SQL, który stworzy nasze tabele (zamiast Hibernate'a):

```sql
-- Tworzenie głównej tabeli transakcji
CREATE TABLE transactions (
    db_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    original_id VARCHAR(255),
    date DATE,
    amount DECIMAL(19, 2),
    currency VARCHAR(10),
    amountpln DECIMAL(19, 2),
    category VARCHAR(255),
    description VARCHAR(255)
);

-- Tworzenie tabeli powiązanej dla tagów (1 transakcja -> wiele tagów)
CREATE TABLE transaction_entity_tags (
    transaction_entity_db_id BIGINT NOT NULL,
    tags VARCHAR(255),
    FOREIGN KEY (transaction_entity_db_id) REFERENCES transactions(db_id)
);
```

Jak to przetestować?
Uruchom aplikację:

./gradlew runSmartFinDb -PappArgs="-u Jacek"

Co powinieneś zauważyć w logach konsoli?
Pojawią się zupełnie nowe linijki od Flyway:
```
INFO  o.f.c.i.database.base.BaseDatabaseType - Database: jdbc:h2:file:./db/smartfin (H2 2.2)
INFO  o.f.core.internal.command.DbValidate - Successfully validated 1 migration
INFO  o.f.core.internal.command.DbMigrate - Migrating schema "PUBLIC" to version "1 - init schema"
```

Dlaczego to jest absolutny "Game Changer"? (Dla Rekrutera)
Jeśli zapytają Cię na rozmowie kwalifikacyjnej: "Jak wdrażasz zmiany w bazie danych na produkcję?", Twoja odpowiedź brzmi teraz:
"Używam Flyway. Kiedy muszę dodać nową kolumnę, nie modyfikuję ręcznie bazy na produkcji ani nie polegam na hibernate.ddl-auto. Tworzę skrypt V2__add_user_id.sql. Kiedy aplikacja startuje (w Kubernetesie czy lokalnie), Flyway sam sprawdza, w jakiej wersji jest baza i aplikuje brakujące skrypty. Dzięki temu baza danych ewoluuje razem z kodem aplikacji i jest w 100% wersjonowana w GIT."
Zrób te 4 kroki, odpal aplikację i daj znać, czy Flyway poprawnie zmigrował Twoją bazę! 🗄️🚀











