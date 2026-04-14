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

!!!!!!!!!!
---
- name: "Install AIT test client {{ vm_host_name }}"
  hosts: "{{ vm_host_name }}"
  become: true
  gather_facts: true
  tasks:

  # -------------------------------------------------------
  # 1. Instalacja oprogramowania testclienta
  # -------------------------------------------------------

  # 1a. Skopiuj pliki repo YUM
   - name: "Copy YUM repository files"
     ansible.builtin.copy:
     src: "{{ item }}"
     dest: /etc/yum.repos.d/
     owner: root
     group: root
     mode: '0644'
     loop: "{{ yum_repo_files }}"
     tags:
      - repos
      - install_sw

  # 1b. Zainstaluj oprogramowanie (istniejąca rola)
   - name: "Install test client software"
     ansible.builtin.include_role:
     name: ait_post_sw
     tags:
      - install_sw

  # -------------------------------------------------------
  # 2. Konfiguracja firewalla
  # -------------------------------------------------------
   - name: "Configure firewall rules"
     ansible.posix.firewalld:
     port: "{{ item.port }}"
     zone: "{{ item.zone | default('public') }}"
     permanent: true
     state: enabled
     immediate: true
     loop: "{{ firewall_rules }}"
     tags:
      - firewall

  # -------------------------------------------------------
  # 3. Utworzenie kontenera PKCS12 (istniejąca rola)
  # -------------------------------------------------------
   - name: "Create PKCS12 container"
     ansible.builtin.include_role:
     name: pkcs12_container
     tags:
      - pkcs12




++++++++++++++++++++++++++++++==   drugie podejście   ++++++++++++++++++++++++


---
- name: "Install AIT test client {{ vm_host_name }}"
  hosts: "{{ vm_host_name }}"
  become: true
  gather_facts: true
  roles:
  # -------------------------------------------------
  # 1. Konfiguracja repozytoriów YUM (MUSI być przed ait_post_sw!)
  # -------------------------------------------------
   - project.lib.yum_config

  # -------------------------------------------------
  # 2. Instalacja oprogramowania testclienta
  # -------------------------------------------------
   - ait_post_sw

  # -------------------------------------------------
  # 3. Generowanie certyfikatu PKCS12
  # -------------------------------------------------
   - ait_lib_generate_pkcs12

  # -------------------------------------------------
  # 4. Konfiguracja firewalla (post_tasks — po rolach)
  # -------------------------------------------------
  post_tasks:
   - name: "Configure firewall for test client"
     # TODO: Ustal z AIT-team jakie porty/reguły
     ansible.posix.firewalld:
     port: "{{ item }}"
     permanent: true
     state: enabled
     immediate: true
     loop: "{{ ait_test_vms_firewall_ports }}"

  vars:
  yum_config_repos:
  - name: appstream
  url: "https://xxxxxxxxx/appstream/"
  - name: baseos
  url: "https://yyyyyyyyy/baseos/"
  - name: epel
  url: "https://zzzzzzzzz/epel/"




++++++++++++++++++++++++++++++==   trzecie podejście   ++++++++++++++++++++++++

---
- name: "Install AIT test client {{ vm_host_name }}"
  hosts: "{{ vm_host_name }}"
  become: true
  gather_facts: true
  roles:
  # 1. Konfiguracja repozytoriów YUM
   - project.lib.yum_config

  # 2. Instalacja oprogramowania testclienta
   - ait_post_sw

  # 3. Konfiguracja firewalla
   - rola_firewalla          # ← nazwa do ustalenia z AIT-team

  # 4. Generowanie certyfikatu PKCS12
   - ait_lib_generate_pkcs12

  vars:
  yum_config_repos:
  - name: appstream
  url: "https://xxxxxxxxx/appstream/"
  - name: baseos
  url: "https://yyyyyyyyy/baseos/"
  - name: epel
  url: "https://zzzzzzzzz/epel/"





Bez tego `vars` rola project.lib.yum_config nie wiedziałaby jakie pliki .repo stworzyć w /etc/yum.repos.d/
i instalacja oprogramowania w następnym kroku by się wysypała.

Uwaga: URL-e repozytoriów (https://xxxxxxxxx/...) to placeholdery z link5 — zapytaj AIT-team o prawidłowe URL-e dla testclientów.
Mogą być inne niż te dla HPC head nodes.

Alternatywnie, zamiast definiować yum_config_repos bezpośrednio w playbooku,
możesz ją umieścić w group_vars/ait_test_vms/vars.yml — efekt będzie ten sam, ale playbook będzie czystszy. To kwestia preferencji zespołu.




-----------------------   czwarte podejście  -----------------------

---
- name: "Install AIT test client {{ vm_host_name }}"
  hosts: "{{ vm_host_name }}"
  become: true
  gather_facts: true
  roles:
  # 1. Konfiguracja repozytoriów YUM
   - project.lib.yum_config

  # 2. Instalacja oprogramowania testclienta
   - ait_post_sw

  # 3. Konfiguracja firewalla
   - proj.firewalld

  # 4. Generowanie certyfikatu PKCS12
   - ait_lib_generate_pkcs12

  vars:
  yum_config_repos:
  - name: appstream
  url: "https://xxxxxxxxx/appstream/"
  - name: baseos
  url: "https://yyyyyyyyy/baseos/"
  - name: epel
  url: "https://zzzzzzzzz/epel/"
        
    !!!!!!!!!!!!!!