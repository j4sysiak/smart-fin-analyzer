Lab74
-----

Lab74--JWT-z-Rolami-i-Zabezpieczanie-Metod--@PreAuthorize
=========================================================

Weszliśmy na poziom, na którym Twoja aplikacja zaczyna przypominać prawdziwy system bankowy. 
Masz już "kłódkę" JWT, ale obecnie każdy, kto ma jakikolwiek poprawny token, może zrobić wszystko. 
W rzeczywistości użytkownik powinien widzieć tylko swoje statystyki, 
a tylko admin powinien móc wgrywać pliki CSV.

Kontynuujemy Etap 2 naszej mapy drogowej: `Role-Based Access Control (RBAC)`.

Cel:
Rozszerzenie tokena JWT o listę uprawnień `Claims`.
Zablokowanie endpointu upload dla zwykłych użytkowników.
Sprawdzenie tego "pancernymi" testami w Spocku.

Krok-1. Rozbudowa JwtService.groovy (Dodawanie ról do "biletu")
---------------------------------------------------------------
Musimy sprawić, aby nasz generator tokenów wpisywał do środka informację o roli (np. `ROLE_ADMIN`).

`src/main/groovy/pl/edu/praktyki/security/JwtService.groovy`

```groovy
// Zaktualizuj metodę generateToken, aby przyjmowała listę ról
    String generateToken(String username, List<String> roles = ["ROLE_USER"]) {
        return Jwts.builder()
                .subject(username)
                .claim("roles", roles) // <-- DODAJEMY ROLE DO TOKENA
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + EXPIRATION_TIME))
                .signWith(key)
                .compact()
    }

    // Dodaj metodę do wyciągania ról
    List<String> extractRoles(String token) {
        def claims = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload()
        
        return claims.get("roles", List.class) ?: []
    }
```
 
Krok-2. Aktualizacja Filtra (JwtAuthenticationFilter.groovy)
------------------------------------------------------------
Bramkarz musi teraz nie tylko sprawdzić dowód, ale też przeczytać, jakie uprawnienia ma gość, i przekazać je do Springa.

```groovy
    // Bramkarz musi nie tylko sprawdzić dowód,
// ale też przeczytać, jakie uprawnienia ma gość, i przekazać je do Springa.
@Override
protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) {
   String authHeader = request.getHeader("Authorization")

   // 1. Sprawdzamy czy nagłówek istnieje i zaczyna się od "Bearer "
   if (authHeader?.startsWith("Bearer ")) {
      String jwt = authHeader.substring(7)

      // 2. Weryfikujemy token
      if (jwtService.isTokenValid(jwt)) {
         String username = jwtService.extractUsername(jwt)
         // WYCIĄGAMY ROLE:
         def roles = jwtService.extractRoles(jwt)

         // Zmieniamy pustą listę [] na listę uprawnień Springa
         def authorities = roles
                 .collect { new org.springframework.security.core.authority.SimpleGrantedAuthority(it) }

         // 3. Tworzymy obiekt "zalogowanego użytkownika" w kontekście Springa
         def authToken = new UsernamePasswordAuthenticationToken(username, null, authorities)
         SecurityContextHolder.context.authentication = authToken
      }
   }
   // 4. Przekazujemy żądanie dalej (do kolejnych filtrów lub kontrolera)
   filterChain.doFilter(request, response)
}
```

Krok-3. Włączenie ochrony metod w SecurityConfig.groovy
-------------------------------------------------------
Musimy aktywować adnotację `@PreAuthorize`.

Dodaj nad klasą `SecurityConfig`:

```groovy
@Configuration
@EnableWebSecurity
@org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity // <-- TO WŁĄCZA OCHRONĘ METOD
class SecurityConfig { ... }
```

Krok-4. Zabezpieczenie Kontrolera (UploadController.groovy)
-----------------------------------------------------------
Teraz kładziemy blokadę na wgrywanie plików.

```groovy
@RestController
@RequestMapping("/api/transactions/upload")
class UploadController {

    @PostMapping
    @org.springframework.security.access.prepost.PreAuthorize("hasRole('ADMIN')") // <-- TYLKO ADMIN!
    ResponseEntity<String> uploadCsv(...) { ... }
}
```

Krok-5. Test Spock – "Weryfikacja Uprawnień" (RbacSpec.groovy)
--------------------------------------------------------------
To jest najważniejszy test dla Mida. Sprawdzamy dwa scenariusze:

 - User ma token, ale nie ma roli `ADMIN` -> dostaje `403`.
 - User ma token z rolą `ADMIN` -> zostaje wpuszczony.

`src/test/groovy/pl/edu/praktyki/web/RbacSpec.groovy`

```groovy
package pl.edu.praktyki.web

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.mock.web.MockMultipartFile
import org.springframework.test.web.servlet.MockMvc
import pl.edu.praktyki.BaseIntegrationSpec
import pl.edu.praktyki.security.JwtService
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@AutoConfigureMockMvc
class RbacSpec extends BaseIntegrationSpec {

    @Autowired MockMvc mvc
    @Autowired JwtService jwtService

    def "zwykły UŻYTKOWNIK nie powinien móc wgrywać plików (403 Forbidden)"() {
        given: "token dla zwykłego usera"
        def userToken = jwtService.generateToken("kowalski", ["ROLE_USER"])
        def file = new MockMultipartFile("file", "test.csv", "text/csv", "data".bytes)

        when: "user próbuje zrobić upload"
        def response = mvc.perform(multipart("/api/transactions/upload")
                .file(file)
                .param("user", "kowalski")
                .header("Authorization", "Bearer $userToken"))

        then: "zostaje odrzucony błędem 403"
        response.andExpect(status().isForbidden())
    }

    def "ADMINISTRATOR powinien móc wgrywać pliki (200 OK)"() {
        given: "token dla admina"
        def adminToken = jwtService.generateToken("boss", ["ROLE_ADMIN"])
        // Przygotuj poprawny CSV, żeby parser nie wybuchł
        def csv = "id,date,amount,currency,category,description\nT1,2026-01-01,10,PLN,X,Y".bytes
        def file = new MockMultipartFile("file", "test.csv", "text/csv", csv)

        when: "admin robi upload"
        def response = mvc.perform(multipart("/api/transactions/upload")
                .file(file)
                .param("user", "boss")
                .header("Authorization", "Bearer $adminToken"))

        then: "zostaje wpuszczony"
        response.andExpect(status().isOk())
    }
}
```

Dlaczego to jest "Enterprise Hardcore"?

Zrozumiałeś, że bezpieczeństwo ma dwie warstwy:

1. Authentication (Uwierzytelnienie): 
   Czy Twoje `JWT` jest prawdziwe? (To zrobił filtr).

2. Authorization (Autoryzacja): 
   Czy jako "User" masz prawo wejść do pokoju "Admina"? (To zrobiło `@PreAuthorize`).

Zadanie dla Ciebie:
Wdroż te 4 punkty. 
Jeśli test RbacSpec przejdzie, oznacza to, że Twój system finansowy jest gotowy na obsługę różnych typów użytkowników.

Daj znać, czy udało Ci się "odbić" zwykłego użytkownika od endpointu uploadu!  

???????????????
-------------------------------------------------------------------------------------------------------------------------------------------
--------------------------------------------------------------------------------------------------------------------------------------------
--------------------------------------------------------------------------------------------------------------------------------------------


# 📋 Koncept zmiennych — dokument na Confluence

---

## 1. Przegląd — co robimy

Jeden wspólny playbook `ait_test_vms.yml` dla trzech testclientów (`t-s052edu`, `t-s005phse`, `t-s016phse`), który wykonuje 4 kroki:

| Krok | Rola | Cel |
|---|---|---|
| 1 | `project.lib.yum_config` | Konfiguracja repozytoriów YUM |
| 2 | `ait_post_sw` | Instalacja oprogramowania testclienta |
| 3 | *Do ustalenia* | Konfiguracja firewalla |
| 4 | `ait_lib_generate_pkcs12` | Generowanie certyfikatu PKCS12 |

---

## 2. Zmienne — skąd pochodzą i gdzie je ustawić

### 2.1 Extravar z DAGa (przekazywany przy uruchomieniu)

| Zmienna | Typ | Opis | Ustawiana w |
|---|---|---|---|
| `vm_host_name` | String | Nazwa hosta testclienta | DAG → `extravars` |

### 2.2 Rola `project.lib.yum_config`

| Zmienna | Typ | Ma default? | Gdzie ustawić? | Opis |
|---|---|---|---|---|
| `yum_config_repos` | List | ❌ NIE | `vars:` w playbooku lub `group_vars/ait_test_vms/` | Lista repozytoriów (name + url) |

> ❓ **Do ustalenia z AIT-team:** Jakie dokładne URL-e repozytoriów dla testclientów?

### 2.3 Rola `ait_post_sw`

| Zmienna | Typ | Ma default? | Gdzie ustawić? | Opis |
|---|---|---|---|---|
| `ait_post_sw_testclient_software` | List | ✅ TAK | `group_vars/ait_test_vms/` jeśli default nie pasuje | Lista oprogramowania do instalacji |
| `ait_post_sw_installation_directories` | List | ✅ TAK | `group_vars/ait_test_vms/` jeśli default nie pasuje | Katalogi instalacyjne |
| `ait_post_sw_xagent_version` | String | ✅ TAK | `group_vars/ait_test_vms/` jeśli default nie pasuje | Wersja XStudio XAgent |
| `ait_post_sw_xagent_directory` | String | ✅ TAK | `group_vars/ait_test_vms/` jeśli default nie pasuje | Katalog XAgent |
| `ait_post_sw_xstudio_xagent_config_source` | String | ⚠️ PUSTY `""` | ✅ `group_vars/ait_test_vms/` | URL do konfiguracji XAgent |
| `ait_post_sw_xstudio_config_files` | List | ✅ TAK | `group_vars/ait_test_vms/` jeśli default nie pasuje | Pliki konfiguracyjne XStudio |

### 2.4 Firewall

| Zmienna | Typ | Ma default? | Gdzie ustawić? | Opis |
|---|---|---|---|---|
| *Do ustalenia* | *Do ustalenia* | *Do ustalenia* | `group_vars/ait_test_vms/` | Porty/reguły firewalla |

> ❓ **Do ustalenia z AIT-team:** Czy istnieje zmigrowana rola firewalla (odpowiednik starego `lib_firewall`)? Jak się nazywa? Jakie zmienne przyjmuje? Jakie porty/reguły otworzyć?

### 2.5 Rola `ait_lib_generate_pkcs12`

| Zmienna | Typ | Ma default? | Gdzie ustawić? | Opis |
|---|---|---|---|---|
| `ait_lib_generate_pkcs12_pki_password` | String | ✅ TAK | `group_vars/ait_test_vms/` lub **vault** | Hasło certyfikatu |
| `ait_lib_generate_pkcs12_operational_domain` | String | ✅ TAK | `group_vars/ait_test_vms/` | Domena operacyjna |
| `ait_lib_generate_pkcs12_host_specific_service_name` | String | ✅ TAK | ⚠️ prawdopodobnie `host_vars/<host>/` | Nazwa serwisu per host |

> ❓ **Do ustalenia z AIT-team:** Czy `ait_lib_generate_pkcs12_host_specific_service_name` jest unikalna per host, czy identyczna dla 3 testclientów?

### 2.6 Zmienne IPA/IDM (dependency roli pkcs12)

| Zmienna | Prawdopodobna nazwa w inventory | Czy już istnieje? |
|---|---|---|
| `ipa_fqn` | `ipa_server_fqn` | ❓ Sprawdzić |
| `ipa_admin_user` | `ipa_server_admin_user` | ❓ Sprawdzić |
| `ipa_admin_password` | `ipa_server_admin_password` | ❓ Sprawdzić |
| `secure_service_ipa_fqn` | ? | ❓ Sprawdzić |
| `secure_service_ipa_admin_user` | ? | ❓ Sprawdzić |
| `secure_service_ipa_admin_password` | ? | ❓ Sprawdzić |

> ❓ **Do ustalenia z AIT-team:** Jak rola `ait_lib_generate_pkcs12` mapuje zmienne IPA w produkcji? Czy korzysta ze zmiennych `ipa_server_*` już istniejących w inventory?

---

## 3. Gdzie trafiają zmienne — schemat

```
inventory/
├── group_vars/
│   ├── all/
│   │   └── vars.yml              ← zmienne IPA (prawdopodobnie już istnieją)
│   └── ait_test_vms/
│       └── vars.yml              ← zmienne wspólne dla 3 testclientów:
│                                    - ait_post_sw_*
│                                    - ait_lib_generate_pkcs12_pki_password
│                                    - ait_lib_generate_pkcs12_operational_domain
│                                    - firewall_* (do ustalenia)
│                                    - yum_config_repos (alternatywnie w playbooku)
│
├── host_vars/
│   ├── t-s052edu/vars.yml        ← zmienne unikalne per host
│   ├── t-s005phse/vars.yml          (np. ait_lib_generate_pkcs12_host_specific_service_name)
│   └── t-s016phse/vars.yml
```

---

## 4. Otwarte pytania do AIT-team

| # | Pytanie | Status |
|---|---|---|
| 1 | Nazwa i zmienne nowej roli firewalla? | ❓ |
| 2 | Jakie URL-e repozytoriów YUM dla testclientów? | ❓ |
| 3 | Czy `ait_lib_generate_pkcs12_host_specific_service_name` jest unikalna per host? | ❓ |
| 4 | Jak rola pkcs12 mapuje zmienne IPA w produkcji? | ❓ |
| 5 | Które zmienne z `ait_post_sw` trzeba nadpisać (czy defaulty pasują)? | ❓ |

---

Powodzenia u ważniaków! 💪 Jak wrócisz z odpowiedziami, pomogę Ci dokończyć implementację i przygotować PR.

--------------------------------------------------------------------------------------------------------------------------------------------
--------------------------------------------------------------------------------------------------------------------------------------------
--------------------------------------------------------------------------------------------------------------------------------------------

aaa, jeszcze firewall , pewnie tam też będa jakieś zmienne do Confluence


Racja! Dodaję sekcję firewalla do konceptu.

---

## Firewall — co wiemy

Ze starego systemu (link1) widzisz, że firewall był konfigurowany przez rolę z jedną zmienną:

```yaml
- name: Configure firewall
  include_role:
    name: lib_firewall
  vars:
    firewall_rules: "{{ rules }}"
```

Czyli w starym systemie była **rola `lib_firewall`** przyjmująca zmienną `firewall_rules` — prawdopodobnie listę portów/reguł.

### Dodatkowa sekcja do tabeli na Confluence:

### 2.4 Firewall (zaktualizowane)

| Zmienna | Typ | Ma default? | Gdzie ustawić? | Opis |
|---|---|---|---|---|
| *Nazwa roli: do ustalenia* | — | — | — | Stara rola: `lib_firewall`, nowa: **?** |
| `firewall_rules` (stara nazwa) | List | ❓ Do ustalenia | `group_vars/ait_test_vms/` | Lista reguł firewalla (porty, protokoły, zony) |

Prawdopodobna struktura zmiennej (na podstawie typowych ról firewalla):

```yaml
firewall_rules:
  - port: "8080/tcp"
    zone: "public"
  - port: "8443/tcp"
    zone: "public"
  # ... kolejne reguły
```

### Dodatkowe pytanie do AIT-team:

| # | Pytanie | Status |
|---|---|---|
| 6 | Jak nazywa się nowa (zmigrowana) rola firewalla? | ❓ |
| 7 | Jakie zmienne przyjmuje nowa rola firewalla? (nazwa, typ, struktura) | ❓ |
| 8 | Jakie konkretne porty/reguły mają być otwarte na testclientach? | ❓ |
| 9 | Czy rola firewalla ma swoje defaulty, czy trzeba podać wszystko ręcznie? | ❓ |

---

Dopóki AIT-team nie odpowie na te pytania, w playbooku zostaje placeholder:

```yaml
    # 3. Konfiguracja firewalla
    - rola_firewalla          # ← nazwa do ustalenia z AIT-team
```

A w tabeli zmiennych na Confluence zaznacz tę sekcję jako **⚠️ DO UZUPEŁNIENIA PO KONSULTACJI Z AIT-TEAM**.

Teraz masz kompletny koncept. Powodzenia! 🚀

??????????????