package pl.edu.praktyki.report

// To jest szkielet - template method - definiujemy tutaj szkielet algorytmu,
// a kroki pozostawia do implementacji klasom pochodnym
abstract class ReportTemplate {

    // TO JEST METODA SZABLONOWA (Template Method)
    // Jest 'final', więc klasy pochodne nie mogą jej zepsuć - wymuszamy kolejność!
    final void generateReport() {
        def data = fetchData()
        def processed = processData(data)
        saveReport(processed)
        logStart()
    }

    // Kroki, które klasy pochodne muszą zaimplementować (wymuszone abstract)
    abstract String fetchData()
    abstract String processData(String data)

    // Krok, który ma domyślną implementację (można nadpisać w klasach pochodnych ale nie ma konieczności)
    void saveReport(String report) {
        println "Zapisuję raport domyślnie do konsoli: $report"
    }

    void logStart() {
        println "--- Rozpoczęto generowanie raportu: ${this.getClass().simpleName} ---"
    }
}