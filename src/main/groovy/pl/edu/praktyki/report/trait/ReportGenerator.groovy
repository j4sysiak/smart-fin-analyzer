package pl.edu.praktyki.report.trait

trait ReportGenerator {

    // Szkielet algorytmu (Metoda Szablonowa wewnątrz Traita)
    void generateReport() {
        println ">>> [TRAIT] Rozpoczynam generowanie..."
        def data = fetchData()
        def processed = processData(data)
        saveReport(processed)
        println ">>> [TRAIT] Raport gotowy!"
    }

    // Wymagamy, aby klasa implementująca miała te metody
    abstract String fetchData()
    abstract String processData(String data)

    // Opcjonalna domyślna implementacja (można nadpisać w klasie)
    void saveReport(String report) {
        println "Zapisuję do pliku: $report"
    }
}