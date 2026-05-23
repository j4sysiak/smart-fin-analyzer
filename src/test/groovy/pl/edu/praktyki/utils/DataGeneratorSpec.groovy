package pl.edu.praktyki.utils

import spock.lang.Specification
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class DataGeneratorSpec extends Specification {

    def "Generuj plik CSV z przykładowymi transakcjami"() {
        given: "Ścieżka do pliku"
        File outputFile = new File("build/tmp/test_transactions.csv")
        int rowCount = 12000

        when: "Generuję plik"
        generateCsv(outputFile, rowCount) // Teraz typy się zgadzają: (File, Integer)

        then: "Plik istnieje"
        outputFile.exists()
    }



    private void generateCsv(File file, int count) {
        // Nie musimy już tworzyć nowego obiektu File wewnątrz metody,
        // skoro dostajemy go w argumencie!

        def formatter = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS")
        Random rand = new Random()

        // Tablice do losowania
        def currencies = ["PLN", "EUR", "USD"]
        // Twoja lista z 24 kategoriami
        def categories =[
                "Zakupy", "Jedzenie", "Praca", "Rozrywka",
                "Transport", "Zdrowie", "Edukacja", "Dom",
                "Media", "Sport", "Podróże", "Inwestycje",
                "Prezenty", "Ubezpieczenia", "Podatki", "Rozrywka",
                "Oprogramowanie", "Sprzęt", "Książki", "Gaming",
                "Restauracje", "Paliwo", "Samochód", "Usługi"
        ]

        // Upewniamy się, że katalog istnieje wewnątrz metody
        file.parentFile.mkdirs()

        file.withWriter('UTF-8') { writer ->
            writer.writeLine("id,amount,currency,category,description,date")

            (1..count).each { i ->
                String timestamp = LocalDateTime.now().format(formatter)
                String id = "TX-${timestamp}-${i}"
                double amount = (rand.nextDouble() * 2000) - 500

                // Losowanie przy użyciu indeksu
                String currency = currencies[rand.nextInt(currencies.size())]
                String category = categories[rand.nextInt(categories.size())]

                String date = "2026-05-11"

                writer.writeLine("${id},${String.format(Locale.US, "%.2f", amount)},${currency},${category},TestItem_${i},${date}")
            }
        }
    }
}