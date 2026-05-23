package pl.edu.praktyki.contract

interface TransactionAnalyzer {
    AnalysisResult analyze(TransactionIngressRequest request)
}