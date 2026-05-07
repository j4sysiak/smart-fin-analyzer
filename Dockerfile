# =============================================================
# ETAP 1 — BUILD
# Kompiluje aplikację wewnątrz kontenera (nie wymaga lokalnego JDK/Gradle)
# =============================================================
FROM gradle:8.7-jdk17-alpine AS builder

WORKDIR /app

# Kopiujemy najpierw pliki konfiguracyjne Gradle (cache warstwy — przebudowa tylko przy zmianie deps)
COPY gradle/ gradle/
COPY gradlew gradlew.bat settings.gradle build.gradle gradle.properties ./

# gradle.properties w repo może zawierać windowsowy org.gradle.java.home dla
# lokalnych uruchomień. W kontenerze Linux usuwamy ten wpis, aby Gradle użył
# JDK dostępnego w obrazie buildera.
RUN sed -i '/^org\.gradle\.java\.home=/d' gradle.properties || true

# Pobieramy zależności (osobna warstwa cache — nie przebudowuje przy zmianie kodu)
RUN gradle dependencies --no-daemon -q || true

# Kopiujemy właściwy kod źródłowy
COPY src/ src/
COPY config/ config/

# Budujemy JAR pomijając testy i analizę statyczną (testy są uruchamiane w CI osobno)
RUN gradle bootJar --no-daemon -x test -x codenarcMain -x codenarcTest

# =============================================================
# ETAP 2 — RUNTIME
# Lekki obraz JRE — tylko uruchomienie, bez narzędzi budowania
# =============================================================
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

# Kopiujemy tylko gotowy JAR z etapu build
COPY --from=builder /app/build/libs/*.jar app.jar

# Port aplikacji Spring Boot
EXPOSE 8080

# Uruchomienie aplikacji
ENTRYPOINT ["java", "-jar", "app.jar"]

