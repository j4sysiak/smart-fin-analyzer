package pl.edu.praktyki.contract

interface TransactionDecisionPolicy {
    TransactionDecision decide(TransactionIngressRequest request, AnalysisResult result)
}