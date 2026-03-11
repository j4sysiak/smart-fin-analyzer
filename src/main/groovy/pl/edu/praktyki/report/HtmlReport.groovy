package pl.edu.praktyki.report

class HtmlReport extends ReportTemplate {
    @Override
    String fetchData() { "Dane z bazy SQL" }

    @Override
    String processData(String data) { "<html><body>$data</body></html>" }

    @Override
    void saveReport(String report) {
        println "Zapisuję raport HTML: $report"
    }
}