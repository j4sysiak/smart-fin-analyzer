package pl.edu.praktyki.report.trait
// Wykorzystujemy nasz Trait z audytu (zrobiony wcześniej)

class FinanceReport implements ReportGenerator, AuditLog {

    @Override
    String fetchData() {
        "Dane finansowe z bazy"
    }

    @Override
    String processData(String data) {
        "Raport: $data przeliczony"
    }

    // Możemy nadpisać saveReport, ale nie musimy
}