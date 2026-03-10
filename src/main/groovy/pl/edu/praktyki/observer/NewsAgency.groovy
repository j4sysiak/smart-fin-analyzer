package pl.edu.praktyki.observer

class NewsAgency {
    // Lista naszych obserwatorów - przyjmujemy dowolne bloki kodu (Closures)
    private List<Closure> listeners =[]

    // Rejestracja obserwatora (Subskrypcja)
    void subscribe(Closure listener) {
        listeners << listener
    }

    // NOWA METODA: Wypisywanie się
    void unsubscribe(Closure listener) {
        listeners.remove(listener)
    }

    // Wywołanie zmiany stanu
    void publishNews(String breakingNews) {
        println ">>>[AGENCJA PRASOWA] Publikuję: $breakingNews"

        // Powiadamiamy wszystkich obserwatorów (odpalamy ich kod)
        listeners.each { it.call(breakingNews) }
    }
}