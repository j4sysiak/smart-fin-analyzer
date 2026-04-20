Lab81
-----

Lab81--Audyt-i-Śledzenie-Zmian--JPA-Auditing
============================================

Skoro system jest już bezpieczny, wydajny i asynchroniczny, czas na kolejny kluczowy element systemów klasy Enterprise. 
Na rozmowach o pracę na stanowisko Mid na pewno padnie pytanie: "Jak śledzicie, kto i kiedy zmodyfikował dane w bazie?".
Wchodzimy w Fasę 7: Audyt i Śledzenie Zmian (JPA Auditing).

Problem: 
Obecnie zapisujemy transakcje, ale nie wiemy, o której godzinie fizycznie wpadły do bazy oraz (co najważniejsze) który zalogowany użytkownik je dodał. 
Dodawanie tego ręcznie w każdym serwisie (tx.setCreatedBy(currentUser)) to błąd i łamanie zasady DRY (??).

Rozwiązanie: 
Użyjemy mechanizmu `Spring Data JPA Auditing`, który automatycznie "wstrzyknie" te dane przy każdym zapisie do bazy.

Krok-1. Nowa migracja Flyway (V7__add_audit_columns.sql)
--------------------------------------------------------
Musimy dodać kolumny do tabeli transakcji, aby mieć gdzie zapisywać te dane.

`src/main/resources/db/migration/V7__add_audit_columns.sql`

```sql
ALTER TABLE transactions
ADD COLUMN created_date TIMESTAMP,
ADD COLUMN last_modified_date TIMESTAMP,
ADD COLUMN created_by VARCHAR(50),
ADD COLUMN last_modified_by VARCHAR(50);
```

Kraok-2. Aktualizacja Encji (TransactionEntity.groovy)
------------------------------------------------------
Dodajemy pola i specjalne adnotacje. Spring będzie ich używał do automatycznego wypełniania danych.

```groovy
package pl.edu.praktyki.repository

import jakarta.persistence.*
import org.springframework.data.annotation.*
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import java.time.LocalDateTime

@Entity
@Table(name = "transactions")
@EntityListeners(AuditingEntityListener.class) // <-- KLUCZOWE
class TransactionEntity {
// ... Twoje poprzednie pola (dbId, originalId, itp.) ...

    @CreatedDate
    @Column(updatable = false)
    LocalDateTime createdDate

    @LastModifiedDate
    LocalDateTime lastModifiedDate

    @CreatedBy
    @Column(updatable = false)
    String createdBy

    @LastModifiedBy
    String lastModifiedBy
    
    // ...
}
```

Krok-3. Implementacja AuditorAware (Most do Security)
-----------------------------------------------------
Skąd Spring ma wiedzieć, kto jest aktualnie zalogowany? 
Musimy mu to powiedzieć, łącząc go z naszym `SecurityContextHolder` (gdzie filtr `JWT` wkłada użytkownika).

Stwórz plik `src/main/groovy/pl/edu/praktyki/security/SecurityAuditorAware.groovy`

```groovy
package pl.edu.praktyki.security

import org.springframework.data.domain.AuditorAware
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component

@Component
class SecurityAuditorAware implements AuditorAware<String> {

    @Override
    Optional<String> getCurrentAuditor() {
        def auth = SecurityContextHolder.getContext().getAuthentication()
        
        if (auth == null || !auth.isAuthenticated() || auth.principal == "anonymousUser") {
            return Optional.of("SYSTEM") // Dla zadań w tle (Scheduler)
        }
        
        return Optional.of(auth.name) // Zwraca username z tokena JWT
    }
}
```

Krok-4. Włączenie Audytu w Konfiguracji
---------------------------------------
Dodaj adnotację `@EnableJpaAuditing` do swojej głównej klasy lub konfiguracji.

```groovy
@SpringBootApplication
@EnableJpaAuditing // <-- DODAJ TO
class SmartFinDbApp { ... }
```

Krok-5. Test Spock – "Wielki Brat patrzy" (AuditingSpec.groovy)
---------------------------------------------------------------
Sprawdzimy, czy po zapisie transakcji przez zalogowanego użytkownika, pole createdBy wypełniło się samo.
Zgodnie z naszą zasadą „Mirroringu” (odzwierciedlania struktury pakietów), 
skoro testujemy mechanizm zapisu encji do bazy danych, klasa ta powinna znaleźć się w pakiecie odpowiadającym repozytorium.
1. Lokalizacja pliku
   Umieść plik w ścieżce:
   src/test/groovy/pl/edu/praktyki/repository/AuditingSpec.groovy
2. Pełny kod klasy AuditingSpec
   Ta klasa dziedziczy po Twoim BaseIntegrationSpec, dzięki czemu test wykona się na prawdziwym PostgreSQL (Docker/Testcontainers). 
   Używamy adnotacji `@WithMockUser`, aby "oszukać" Springa, że do systemu zapukał konkretny użytkownik.


```groovy
package pl.edu.praktyki.repository

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.test.context.support.WithMockUser
import pl.edu.praktyki.BaseIntegrationSpec
import spock.lang.Specification
import java.time.LocalDateTime

class AuditingSpec extends BaseIntegrationSpec {

    @Autowired
    TransactionRepository repository

    def setup() {
        // Czyścimy bazę przed testem audytu
        repository.deleteAll()
    }

    @WithMockUser(username = "jacek_manager")
    def "powinien automatycznie zapisać informację o autorze i dacie utworzenia (JPA Auditing)"() {
        given: "nowa encja transakcji (nie ustawiamy pól audytowych ręcznie!)"
        def entity = new TransactionEntity(
                originalId: "AUDIT-TEST-1",
                amount: 1000.0,
                amountPLN: 1000.0,
                category: "PROCES",
                description: "Test automatycznego audytu"
        )

        when: "zapisujemy encję do bazy danych"
        def saved = repository.saveAndFlush(entity)

        then: "Spring Security przekazał nazwę użytkownika do JPA"
        saved.createdBy == "jacek_manager"
        saved.lastModifiedBy == "jacek_manager"

        and: "pola daty zostały automatycznie wypełnione"
        saved.createdDate != null
        saved.lastModifiedDate != null

        // Sprawdzamy czy data jest z dzisiaj (z dokładnością do minuty)
        saved.createdDate.isBefore(LocalDateTime.now().plusSeconds(1))

        and: "wyświetlamy dowód w logach"
        println "--------------------------------------------------------"
        println "LOG AUDYTOWY:"
        println "Autor: ${saved.createdBy}"
        println "Data utworzenia: ${saved.createdDate}"
        println "--------------------------------------------------------"
    }

    def "powinien oznaczyć wpis jako SYSTEM gdy brak zalogowanego użytkownika"() {
        given: "transakcja zapisywana bez kontekstu security (np. przez automat)"
        def entity = new TransactionEntity(originalId: "SYS-1", amount: 10.0, category: "SYS")

        when:
        def saved = repository.saveAndFlush(entity)

        then: "zadziałała logika z Twojej klasy SecurityAuditorAware"
        saved.createdBy == "SYSTEM"
    }
}
```

Dlaczego to jest poziom Mid?
----------------------------
Separacja Odpowiedzialności: 
Logika biznesowa nie zajmuje się czasem zapisu. Zajmuje się tym infrastruktura.

Integracja modułów: 
Połączyłeś bazę danych `JPA` z bezpieczeństwem `Spring Security` w sposób niewidoczny dla użytkownika.

Traceability: 
Twój system jest gotowy na audyt finansowy. Możesz udowodnić, kto dokładnie dodał każdą złotówkę do systemu.

Zadanie:
--------
Wdroż migrację V7 i pola audytowe.
Dodaj SecurityAuditorAware.
Uruchom test i sprawdź, czy Twoje nazwisko (lub "admin") pojawia się w nowej kolumnie created_by w DBeaverze.

# Lookup z OpenBao:
ait_lib_generate_pkcs12_pki_password: "{{ lookup('community.hashi_vault.vault_kv2_get', 'testclient', engine_mount_point='kv2-apps-ait').secret.pki_password }}"



pre_tasks:
- name: Generate and store PKI password in OpenBao
community.hashi_vault.vault_kv2_write:
engine_mount_point: kv2-apps-ait
path: testclient
data:
pki_password: "{{ lookup('password', '/dev/null length=32 chars=ascii_letters,digits') }}"
run_once: true    # ← wykona się TYLKO RAZ, nie 3 razy per host


Co musisz jeszcze sprawdzić
Poszukaj w kodzie przykładu — ważniak mówi "see for examples in code for only one password". Znajdź w repo kto już używa community.hashi_vault.vault_kv2_write i wzoruj się na tym
Nazwa klucza — ważniak napisał "Then name key:" ale nie dokończył. Zapytaj go jak ma się nazywać klucz (np. pki_password?)
Składnia lookupu — może się różnić w zależności od konfiguracji OpenBao w Waszym środowisku