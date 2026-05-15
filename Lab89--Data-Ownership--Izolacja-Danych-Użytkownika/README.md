Lab89
-----

Lab89--Data-Ownership--Izolacja-Danych-Użytkownika
=================================================

Skoro system potrafi już:
- uwierzytelniać użytkowników (`Spring Security` + `JWT`),
- zapisywać autora operacji (`JPA Auditing` — Lab 81),
- pamiętać historię zmian (`transactions_aud`, `revinfo`, `Envers`/manualny audit — Lab 88),

to czas zamknąć bardzo ważną lukę biznesową.

Problem:
--------
Obecnie każdy zalogowany użytkownik może odczytać wszystkie transakcje w systemie.
W aplikacji finansowej to niedopuszczalne.

Samo pole `createdBy` nie rozwiązuje tego problemu.
`createdBy` mówi nam:
- kto technicznie wykonał zapis,

ale nie mówi:
- do kogo biznesowo należą dane.

Przykład:
- `admin` może zaimportować CSV,
- task systemowy może zapisać rekord asynchronicznie,
- ale właścicielem transakcji nadal powinien być konkretny użytkownik.

Rozwiązanie:
------------
Wprowadzamy **Data Ownership**, czyli jawne powiązanie transakcji z właścicielem,
a następnie dokładamy filtrację odczytu na poziomie aplikacji.

To ważne: ten lab **nie jest jeszcze prawdziwym PostgreSQL Row Level Security** - `PostgreSQL RLS`.
To jest:
- `application-level data isolation`,
- czyli filtrowanie danych po zalogowanym użytkowniku w warstwie `Spring Security` + `JPA`.

To jest bardzo dobry, praktyczny i realistyczny krok "mid-level".
Prawdziwe `PostgreSQL RLS` można zrobić jako osobny, późniejszy lab.

Cel:
----
1. Każda transakcja ma właściciela biznesowego.
2. Zwykły użytkownik widzi tylko swoje rekordy.
3. `admin` może mieć osobną, świadomie uprzywilejowaną ścieżkę.
4. Audit historyczny pamięta także właściciela rekordu.
5. Upload CSV i zwykły REST zapisują spójne dane ownership.

Krok-1. Nowa migracja Flyway (`V15__add_owner_username_to_transactions.sql`)
----------------------------------------------------------------------------
Na tym etapie wybieramy prostszy i bardzo praktyczny wariant:
`owner_username` jako zwykłą kolumnę tekstową.

Dlaczego nie od razu `owner_id`?
- `JWT` i `SecurityContext` już dziś pracują na `username`,
- migracja jest prostsza,
- testy są prostsze,
- ręczny audit dla `transactions_aud` wymaga mniejszej liczby zmian,
- to bardzo dobry etap pośredni przed ewentualnym przejściem na `owner_id` i prawdziwe `RLS`.

`src/main/resources/db/migration/V15__add_owner_username_to_transactions.sql`

```sql
ALTER TABLE transactions
    ADD COLUMN owner_username VARCHAR(50);

CREATE INDEX IF NOT EXISTS idx_transactions_owner_username
    ON transactions(owner_username);

-- Dla istniejących rekordów ustawiamy właściciela technicznego,
-- żeby migracja nie zostawiła pustych danych historycznych.
UPDATE transactions
SET owner_username = COALESCE(created_by, 'admin')
WHERE owner_username IS NULL;

ALTER TABLE transactions_aud
    ADD COLUMN owner_username VARCHAR(50);
```

Uwaga praktyczna:
- jeśli chcesz później zaostrzyć model, możesz dodać `NOT NULL` dopiero po backfillu i po przejściu testów,
- na start bezpieczniej jest zrobić migrację etapami.

Krok-2. Aktualizacja `TransactionEntity` — właściciel biznesowy
---------------------------------------------------------------
Do encji transakcji dokładamy osobne pole ownership, niezależne od `createdBy`.

Przykład koncepcyjny:

```groovy
@Column(name = "owner_username")
private String ownerUsername
```

Ważne rozróżnienie:
- `createdBy` = kto wykonał zapis,
- `ownerUsername` = do kogo należy rekord.

Te pola czasem będą miały tę samą wartość, ale nie zawsze.

Przykłady:
- zwykły użytkownik zapisuje swoją transakcję → `createdBy = user1`, `ownerUsername = user1`,
- admin importuje dane dla klienta → `createdBy = admin`, `ownerUsername = klient1`,
- scheduler zapisuje coś technicznie → `createdBy = SYSTEM`, `ownerUsername = user1`.

Krok-3. Provider aktualnego użytkownika zamiast rozsianego `SecurityContextHolder`
-----------------------------------------------------------------------------------
Zamiast pisać w każdym serwisie bezpośrednio:

```groovy
SecurityContextHolder.getContext().getAuthentication().getName()
```

lepiej ukryć to za małym komponentem, np.:
- `CurrentUserProvider`,
- `OwnershipService`,
- albo `UserContextService`.

Taki komponent powinien:
- zwracać aktualny `username`,
- rozpoznawać `anonymousUser`,
- obsługiwać przypadek `SYSTEM`,
- nie robić ślepo `.get()` na `Optional` z repozytorium użytkowników.

Dzięki temu logika ownership jest:
- testowalna,
- spójna,
- łatwa do ponownego użycia.

Tworzymy więc klasę `UserContextService` (Twój "Provider")
Zamiast powtarzać logikę wyciągania usera z SecurityContext, zróbmy to raz a dobrze.
Stwórz `src/main/groovy/pl/edu/praktyki/security/UserContextService.groovy`:


```groovy
package pl.edu.praktyki.security

import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Service

@Service
class UserContextService {

   String getCurrentUsername() {
      def auth = SecurityContextHolder.context.authentication
      if (auth == null || !auth.authenticated || auth.principal == "anonymousUser") {
         return "SYSTEM"
      }
      return auth.name
   }
}
```

Krok-4. Repozytorium i filtrowanie odczytu
------------------------------------------
To jest sedno labu.
Aktualizacja Repozytorium i Specyfikacji
To jest najważniejszy moment – zamykamy wyciek danych
Nie wystarczy dodać jednej metody typu:

```groovy
findAllByOwnerUsername(String ownerUsername, Pageable pageable)
```

Trzeba przejrzeć wszystkie ścieżki odczytu, które dziś działają globalnie:
- `GET /api/transactions`,
- `GET /api/transactions/{dbId}`,
- `GET /api/transactions/search`,
- statystyki liczone z historii,
- odczyty wykonywane po zapisie w fasadzie,
- ewentualne read modele i projekcje.

Dobra zasada:
- zwykły użytkownik dostaje zawsze filtr `owner_username = currentUser`,
- admin ma osobną ścieżkę lub jawny override,
- nie wolno zostawić żadnego bocznego endpointu, który czyta globalnie.

Jeśli używasz `Specification`, dokładamy ownership jako jeden z warunków bazowych,
a nie jako opcjonalny filtr podawany przez klienta.

1. W TransactionRepository.groovy dodaj:

```groovy
Page<TransactionEntity> findAllByOwnerUsername(String ownerUsername, Pageable pageable)
```

2. Kluczowa zmiana w `TransactionSpecifications.groovy`:
   Musimy sprawdzić, czy każde wyszukiwanie (nawet to dynamiczne) jest ograniczone do właściciela.

```groovy
static Specification<TransactionEntity> isOwnedBy(String username) {
        return (root, query, cb) -> cb.equal(root.get("ownerUsername"), username)
    }
```

3. Poprawka w `TransactionBulkSaver` (Twoja potężna klasa)
   UWAGA: Ponieważ używasz natywnego SQL (COPY i INSERT), dodanie kolumny w bazie wymusza aktualizację Twojego kodu SQL, inaczej zapis wybuchnie!
   W TransactionBulkSaver.groovy musisz dodać owner_username do listy kolumn w zapytaniach:

```groovy
// Przykład dla JdbcTemplate:
String sql = "insert into transactions (..., owner_username) values (..., ?)"
// i w setValues:
ps.setString(14, e.ownerUsername)
```


Krok-5. Zapis danych — REST, bulk i upload CSV
-----------------------------------------------
Ownership trzeba ustawiać nie tylko przy zwykłym `POST /api/transactions`,
ale też we wszystkich innych ścieżkach zapisu.

W tym projekcie szczególnie ważne są:
- zwykły zapis przez repozytorium,
- bulk save,
- upload CSV,
- ścieżki asynchroniczne.

Najważniejsza zasada:
- właściciel danych nie powinien być brany bezrefleksyjnie z payloadu klienta,
- zwykły użytkownik nie powinien móc powiedzieć: "zapisz to jako ktoś inny".

Dla wersji v1 najlepiej przyjąć:
- zwykły user zapisuje zawsze dla siebie,
- `admin` może mieć osobny, świadomy mechanizm importu dla wskazanego użytkownika,
- parametr typu `?user=...` nie powinien być jedynym źródłem prawdy dla ownership.



Krok-6. Audit historyczny też musi pamiętać właściciela
-------------------------------------------------------
W tym projekcie `TransactionEntity` nie korzysta w pełni z prostego modelu `@Audited` dla całej encji.
Dla transakcji działa manualny zapis do:
- `transactions_aud`,
- `revinfo`.

Dlatego ownership trzeba dopiąć także tutaj:
- nowa kolumna w `transactions_aud`,
- aktualizacja manualnego writera audytu,
- test, że insert/update zachowują ownera w historii.

To bardzo ważne, bo inaczej:
- tabela biznesowa będzie miała ownera,
- ale historia zmian już nie,
- a więc audyt stanie się niepełny.



Krok-7. Test Spock — "The Privacy Test"
----------------------------------------
To nie powinien być tylko jeden test serwisowy.
Najlepiej rozbić to na kilka poziomów.

1. Test repozytorium / specyfikacji
   - użytkownik `userA` widzi tylko swoje rekordy,
   - użytkownik `userB` widzi tylko swoje rekordy.

2. Test REST
   - `GET /api/transactions` nie zwraca cudzych danych,
   - `GET /api/transactions/{dbId}` dla obcego rekordu kończy się `404` lub `403` zgodnie z przyjętą polityką,
   - `GET /api/transactions/search` nie pozwala ominąć ownership przez sprytne filtry.

3. Test uploadu CSV
   - po imporcie rekordy mają poprawnego właściciela,
   - audit w `transactions_aud` zawiera poprawny `owner_username`.

4. Test admina
   - admin może korzystać z endpointu administracyjnego,
   - zwykły użytkownik nie.

Przykładowy scenariusz testowy:

```groovy
def "użytkownik powinien widzieć tylko swoje transakcje"() {
    given: "dwóch użytkowników i po jednej transakcji dla każdego"
    // userA -> TX-A
    // userB -> TX-B

    when: "logujemy się jako userA"
    // wywołanie endpointu /api/transactions

    then: "w odpowiedzi jest tylko TX-A"
}
```

Dlaczego to jest poziom Mid?
----------------------------
Bo tu spotykają się jednocześnie:

1. Bezpieczeństwo
   - użytkownik nie może zobaczyć cudzych danych.

2. Modelowanie domeny
   - odróżniasz autora technicznego od właściciela biznesowego.

3. Spójność architektury
   - ownership musi działać w REST, batchu, async i audycie.

4. Myślenie systemowe
   - nie zabezpieczasz tylko jednego endpointu,
   - tylko cały przepływ danych end-to-end.

To jest dokładnie ten moment, w którym system przestaje być "aplikacją CRUD dla wszystkich"
a zaczyna przypominać realny produkt SaaS.

Najważniejsza różnica: Audyt ≠ Autoryzacja
------------------------------------------
To jest bardzo ważne pytanie na rozmowie.

- `JPA Auditing` odpowiada na pytanie: **kto wykonał zapis i kiedy?**
- `Data Ownership` odpowiada na pytanie: **czyje to są dane i kto może je zobaczyć?**
- `Envers` / `transactions_aud` odpowiada na pytanie: **jak rekord zmieniał się w czasie?**

Te trzy mechanizmy się uzupełniają, ale nie zastępują.

Zadanie:
--------
1. Dodaj migrację `V15__add_owner_username_to_transactions.sql`.
2. Rozszerz model transakcji o `ownerUsername`.
3. Wymuś filtrowanie odczytu po aktualnym użytkowniku.
4. Uporządkuj upload i bulk save tak, aby ownership był nadawany spójnie.
5. Rozszerz audit historyczny o właściciela.
6. Napisz testy, które udowodnią, że użytkownik A nie widzi danych użytkownika B.

Wskazówka architektoniczna:
---------------------------
Jeśli ten lab przejdzie dobrze, naturalnym kolejnym krokiem będzie dopiero:
- przejście z `owner_username` na `owner_id`,
- albo osobny lab z prawdziwym `PostgreSQL Row Level Security` (`CREATE POLICY`, `ENABLE ROW LEVEL SECURITY`).

Na dziś jednak najważniejsze jest jedno:
**najpierw domknij ownership i izolację danych w aplikacji, zanim zejdziesz poziom niżej do polityk bazy.**

