Lab72
-----

Lab72--Ujednolicenie-baz-danych-na-produkcji-i-testach-do-Postgresa---wywalenie-H2
----------------------------------------------------------------------------------

H2 w aplikacji i Postgres w testach to przepis na katastrofę.
Skoro masz zainstalowanego Dockera (bo działają Ci Testcontainers), 
to wyrzucamy H2 do śmietnika i ujednolicamy wszystko na PostgreSQL. 
Dzięki temu Twoje skrypty `Flyway` będą pisane pod jeden silnik i znikną problemy z "Missing Sequence" czy "NoSuchElementException".

Oto plan ratunkowy "Czyste Środowisko" w 3 krokach:

Krok 1: Docker Compose dla Twojej Aplikacji
-------------------------------------------
Zamiast polegać na H2, będziesz miał prawdziwego Postgresa, który działa sobie "obok" w Dockerze.
Skoro port `5433` przez aplikację Minibank (ta z pythona) jest zajęty, 
a Twoje testy i konfiguracja `local-pg` celują w `5432`, to po prostu zróbmy z tego portu nasz nowy standard projektowy.
Oto jak to poukładać, żebyś miał "jeden port, by wszystkimi rządzić" (przynajmniej w tym projekcie):

W głównym folderze projektu stwórz plik docker-compose.yml:

```yml
version: '3.8'
services:
  db:
    image: postgres:16-alpine
    container_name: smartfin-postgres
    ports:
      - "5432:5432" # Lewo: Twój komputer (Host), Prawo: Wnętrze kontenera
    environment:
      POSTGRES_USER: finuser
      POSTGRES_PASSWORD: finpass
      POSTGRES_DB: smartfin_db
    volumes:
      - ./postgres_data:/var/lib/postgresql/data # Opcjonalnie: dane przetrwają restart kontenera
```

Jak to odpalić? Otwórz terminal i wpisz: 
`docker-compose up -d`

Masz teraz na komputerze postawioną bazę identyczną z tą, którą Spock odpala w testach.

Krok 2: Ujednolicenie application.properties
--------------------------------------------
Teraz Twoja aplikacja (nie testy!) będzie łączyć się z tym Postgresem.

Otwórz `src/main/resources/application.properties` i ustaw:

```properties
# =================================================
# PRODUCTION / DEV CONFIGURATION (Standard Port 5432)
# =================================================
spring.datasource.url=jdbc:postgresql://localhost:5432/smartfin_db
spring.datasource.username=finuser
spring.datasource.password=finpass
spring.datasource.driverClassName=org.postgresql.Driver

spring.jpa.hibernate.ddl-auto=validate
spring.flyway.enabled=true
spring.flyway.baseline-on-migrate=true

# Twoje optymalizacje wydajnościowe
spring.jpa.properties.hibernate.jdbc.batch_size=50
spring.jpa.properties.hibernate.order_inserts=true
spring.jpa.properties.hibernate.order_updates=true

# Monitoring i Scheduler
management.endpoints.web.exposure.include=*
app.scheduling.enabled=true
```


Testowy `application-local-pg.properties`
Profil, którego używasz w Spocku, gdy chcesz ręcznie zajrzeć do bazy w Docker.

```properties
# ======================================================================================
# PROFIL: local-pg - Ręczna inspekcja bazy danych (Standard Port 5432)
# ======================================================================================
# Celujemy w OSOBNĄ bazę testową na tym samym serwerze Docker
spring.datasource.url=jdbc:postgresql://localhost:5432/smartfin_test
spring.datasource.username=finuser
spring.datasource.password=finpass

# W testach lokalnych chcemy, żeby Hibernate czyścił wszystko po każdym teście
spring.jpa.hibernate.ddl-auto=create-drop
spring.flyway.enabled=false

app.scheduling.enabled=false
```

Krok 3: Poprawka w Encjach (Sekwencje)
--------------------------------------
Postgres kocha sekwencje. 
Upewnij się, że Twoja encja `TransactionEntity` używa sekwencji, którą stworzyłeś w Flyway.

```groovy
@Id
@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "tx_gen")
@SequenceGenerator(name = "tx_gen", sequenceName = "tx_seq", allocationSize = 50)
Long dbId
```

Dlaczego to Cię uratuje?

Jeden Dialekt SQL: 
Piszesz skrypt Flyway V4__... raz. 
Będzie działał w `runSmartFinDb` i w testach integracyjnych.

Zero magii H2: 
H2 próbuje udawać Postgresa, ale robi to słabo. Teraz używasz oryginału.

DBeaver: 
Twoje połączenie w DBeaverze będzie teraz stałe (localhost:5432). 
Nie musisz niczego Disconnectować, żeby odpalić apkę – Postgres obsługuje tysiące połączeń naraz.

Co masz teraz zrobić?

Zrób:  `docker-compose up -d`.

Zmień `application.properties` (jak wyżej).

Usuń (jeśli chcesz) folder db/ – już go nie potrzebujemy, H2 odchodzi w niepamięć.

Uruchom aplikację: `  ./gradlew runSmartFinDb -PappArgs="-u Jacek -f transakcje.csv"  `.

Jeśli to zrobisz, poczujesz niesamowitą ulgę. System stanie się przewidywalny. Daj znać, czy Postgres w Dockerze "zatrybił" i czy Twoje ciśnienie spadło do normy! 😉🚀⚡

