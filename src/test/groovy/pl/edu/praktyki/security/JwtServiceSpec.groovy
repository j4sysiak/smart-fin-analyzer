package pl.edu.praktyki.security

import spock.lang.Specification

class JwtServiceSpec extends Specification {

    // Tworzymy serwis ręcznie
    def jwtService = new JwtService()

    def "powinien wygenerować i poprawnie zweryfikować token JWT"() {
        given: "nazwa użytkownika"
        def user = "admin"

        when: "generujemy token"
        def token = jwtService.generateToken(user)
        println ">>> Wygenerowany token JWT: $token"

        then: "token został stworzony"
        token != null
        token.startsWith("eyJ") // Każdy token JWT zaczyna się od eyJ

        and: "weryfikacja podpisem przechodzi pomyślnie"
        jwtService.isTokenValid(token) == true

        and: "możemy z niego odzyskać nazwę użytkownika"
        jwtService.extractUsername(token) == "admin"
    }

    def "powinien odrzucić sfałszowany token"() {
        given: "prawdziwy token"
        def token = jwtService.generateToken("admin")

        and: "haker podmienia znak w ŚRODKU tokena (np. podmienia dane w payloadzie lub podpisie)"
        // Bierzemy początek tokena, wstawiamy złośliwe 'X' i doklejamy resztę
        def fakeToken = token.substring(0, 20) + "X" + token.substring(21)
        println ">>> Próba weryfikacji sfałszowanego tokena: $fakeToken"

        expect: "weryfikacja kryptograficzna musi to wyłapać i zwrócić false"
        jwtService.isTokenValid(fakeToken) == false
        // LUB po prostu:
        // !jwtService.isTokenValid(fakeToken)
    }
}