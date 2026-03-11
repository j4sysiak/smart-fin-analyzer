package pl.edu.praktyki.report

class CsvReport extends ReportTemplate {

    @Override
    String fetchData() {
        // Symulacja pobrania danych, które w CSV będą miały nagłówek i wiersz
        return "ID,KWOTA,DATA\n1,500.00,2026-03-10"
    }

    @Override
    String processData(String data) {
        // Tutaj moglibyśmy np. zamienić kropki na przecinki,
        // żeby plik był kompatybilny z polskim Excelem
        return data.replace('.', ',')
    }

    @Override
    void saveReport(String report) {
        println "Zapisuję raport CSV do pliku: $report"
        // W prawdziwym projekcie tutaj użyłbyś: new File("raport.csv").text = report
    }
}