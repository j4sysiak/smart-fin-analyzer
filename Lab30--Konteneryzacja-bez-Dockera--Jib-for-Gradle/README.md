Lab 30
------

Lab 30: Konteneryzacja bez Dockera (Jib for Gradle)
---------------------------------------------------

Cel: 
Zbudowanie obrazu Dockerowego naszej aplikacji bez pisania pliku Dockerfile i bez instalowania demona Dockera (do samego zbudowania).

Google stworzyło narzędzie Jib, które integruje się z Gradle.

Krok 30.1: Dodanie pluginu Jib
------------------------------

Otwórz build.gradle i dodaj w sekcji plugins:

```groovy
plugins {
    // ... poprzednie
    id 'com.google.cloud.tools.jib' version '3.4.0'
}
```

Krok 30.2: Konfiguracja obrazu
------------------------------

Dodaj na końcu build.gradle:

```groovy
jib {
    to {
        image = 'smart-fin-analyzer:latest' // Nazwa twojego obrazu
    }
    container {
        // Wskazujemy główną klasę (tę z bazą danych)
        mainClass = 'pl.edu.praktyki.SmartFinDbApp'
        creationTime = 'USE_CURRENT_TIMESTAMP'
    }
}
```

Krok 30.3: Budowanie obrazu (Build to Tar)
------------------------------------------

Jeśli nie masz zainstalowanego Dockera na komputerze, możesz zbudować obraz do pliku .tar.
W terminalu:

./gradlew jibBuildTar

Po zakończeniu w folderze `build/jib-image.tar` znajdziesz gotowy obraz kontenera!
(Jeśli masz Dockera, możesz wpisać `./gradlew jibDockerBuild`, a obraz trafi prosto do Twojego lokalnego Dockera).


