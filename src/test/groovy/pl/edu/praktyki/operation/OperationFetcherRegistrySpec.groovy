package pl.edu.praktyki.operation

import spock.lang.Specification

class OperationFetcherRegistrySpec extends Specification {

    def "powinien pobierać dane dla typu niezależnie od wielkości liter"() {
        given:
        def bankOperationClient = Stub(BankOperationClient) {
            fetchDeposits() >> [new OperationDto(operationId: "OP-D-1", operationType: "DEPOSIT")]
        }
        def registry = new OperationFetcherRegistry(bankOperationClient)

        when:
        def result = registry.fetchByType("deposit")

        then:
        result.size() == 1
        result[0].operationId == "OP-D-1"
    }

    def "powinien zwrócić null dla nieznanego typu"() {
        given:
        def bankOperationClient = Stub(BankOperationClient)
        def registry = new OperationFetcherRegistry(bankOperationClient)

        expect:
        registry.fetchByType("UNKNOWN_X") == null
    }

    def "powinien zwrócić null dla pustego typu"() {
        given:
        def client = Stub(BankOperationClient)
        def registry = new OperationFetcherRegistry(client)

        expect:
        registry.fetchByType(null) == null
        registry.fetchByType("") == null
    }
}