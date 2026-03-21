# Smart-Fin-Analyzer 📊🚀

A modern, multi-threaded, polyglot (Java/Groovy) CLI application for financial transaction analysis, built on top of **Spring Boot 3**.

## 📖 Overview
Smart-Fin-Analyzer is an enterprise-grade command-line tool designed to ingest, process, and analyze financial data. It demonstrates the powerful synergy between the Java ecosystem (stability, Spring Framework) and Groovy (expressiveness, dynamic rules, fast I/O).

## 🛠️ Tech Stack
* **Core:** Java 17, Groovy 4.0
* **Framework:** Spring Boot 3.2, Spring Data JPA
* **Concurrency:** GPars (Groovy Parallel Systems)
* **Database:** H2 (File-based local storage)
* **Testing:** Spock Framework 2.3 (BDD)
* **Build Tool:** Gradle 8.x (with Toolchains)
* **Integration:** Java `HttpClient` (REST API integration)

## ✨ Key Features
1. **Multi-threaded Ingestion:** Utilizes `GParsPool` to process large batches of transactions across multiple CPU cores simultaneously.
2. **Dynamic Rule Engine:** Employs `GroovyShell` with a `SecureASTCustomizer` sandbox to apply user-defined business rules (e.g., tagging transactions) safely at runtime.
3. **Live Currency Conversion:** Integrates with an external REST API using native Java 11+ `HttpClient` to normalize multi-currency transactions into a base currency (PLN).
4. **Data Persistence:** Uses Hibernate/JPA to store historical transactions, clearly separating the Domain Model (POGO) from the Database Entities.
5. **CLI Orchestration:** Implements Groovy's `CliBuilder` (via Picocli) for a professional terminal user experience (flags, default values, error handling).
6. **Template Reporting:** Generates formatted financial summaries using Groovy's `SimpleTemplateEngine`.

## 🚀 How to Run

The project provides a Gradle wrapper, so no local Groovy or Java installation is strictly required (Gradle will provision JDK 17 automatically via Toolchains).

**Run the standard CLI version:**
```bash
./gradlew runSmartFin -PappArgs="-u 'Your Name' -c EUR"
```

Run the Database-backed version (History tracking):
```bash
./gradlew runSmartFinDb -PappArgs="-u 'Your Name' -c USD"
```


## ✨ Options:
-u, --user : User name for the report (Required)
-c, --currency : Target currency (Optional, defaults to PLN)


## ✨ Testing
The application is fully tested using Spock Framework, covering Unit Tests, Integration Tests with @SpringBootTest, and dynamic AST validations.
```bash
./gradlew test
```

## ✨ Architecture Highlights
-----------------------------
1. **Clean Architecture: Strict separation between the Transaction domain object and TransactionEntity JPA object.
2. **Traits: Utilizes Groovy Traits for composable, cross-cutting concerns (e.g., AuditLog).
3. **DSL & Metaprogramming: Custom Object Graph Builders and closures for highly readable data manipulation.

## 🔌 PluginManager
The `PluginManager` class (`pl.edu.praktyki.utils.PluginManager`) is a lightweight, closure-based plugin system 
that leverages Groovy's first-class support for closures.

**How it works:**
- Plugins are registered as Groovy `Closure` objects via `addPlugin(Closure)` and stored in a private list.
- Calling `runAll(Object data)` executes every registered plugin sequentially, passing the same `data` object to each one.

**Example usage:**
```groovy
def manager = new PluginManager()

manager.addPlugin { tx -> println "Audit: ${tx.id}" }
manager.addPlugin { tx -> if (tx.amount < 0) tx.addTag('EXPENSE') }

manager.runAll(myTransaction)
```

This pattern enables a dynamic, extensible pipeline — 
   new processing steps can be added at runtime without modifying existing code, following the **Open/Closed Principle**.



## 🧩 Architektura i Wzorce Projektowe
System został zaprojektowany z wykorzystaniem najlepszych praktyk inżynierskich:
* **Chain of Responsibility:** Logika walidacji transakcji (Fraud Detection) rozbita na niezależne jednostki.
* **Strategy Pattern:** Dynamiczne wybieranie logiki procesowania (`VipOrderStrategy`, `StandardOrderStrategy`).
* **Proxy / AOP:** Przechwytywanie wywołań metod dla celów logowania czasu i bezpieczeństwa.
* **Facade:** Uproszczony interfejs (`SmartFinFacade`) ukrywający złożoność całego systemu.
* **Composite:** Rekurencyjne drzewa danych (Portfele inwestycyjne) z zachowaniem zasady Liskov.
* **Singleton (Spring Scope):** Świadome zarządzanie stanem aplikacji w środowisku wielowątkowym.
  Czy to wyczerpuje temat?

TAK. Jeśli rozumiesz te wzorce i potrafisz je zaimplementować w Groovym, to jesteś na poziomie,
który pozwala Ci wejść do dowolnego projektu opartego na JVM i szybko połapać się w jego architekturze.

## 🧪 Zaawansowana Architektura Testów (Dual-Profile)
Projekt wykorzystuje profesjonalne podejście do testów integracyjnych, oddzielając środowisko CI/CD 
od środowiska deweloperskiego za pomocą profili Spring:
* **Profil domyślny (`Testcontainers`):** Automatycznie podnosi ulotny kontener PostgreSQL w Dockerze. 
* Gwarantuje to 100% zgodności z produkcją i bezobsługowe działanie na serwerach CI (Jenkins/GitLab CI).
* **Profil `local-pg`:** Przeznaczony do głębokiego debugowania. Łączy się z lokalną instancją PostgreSQL, 
* co pozwala na inspekcję danych "na żywo" przy użyciu narzędzi takich jak DBeaver w trakcie wstrzymania (breakpoint) testu.

