package pl.edu.praktyki.operation

import org.springframework.stereotype.Component

@Component
class OperationValidator {

    boolean isValid(OperationDto op) {
        if (!op?.operationId) return false
        if (!op?.operationType) return false
        if (op.amount == null || op.amount <= 0) return false
        if (!op?.sourceCurrency) return false

        switch (op.operationType) {
            case "DEPOSIT":
                return op.targetAccount != null

            case "WITHDRAWAL":
                return op.sourceAccount != null

            case "TRANSFER":
                return op.sourceAccount != null && op.targetAccount != null

            case "CONVERSION":
                return op.sourceAccount != null &&
                        op.targetCurrency != null &&
                        op.fxRate != null &&
                        op.fxRate > 0

            default:
                return false
        }
    }
}