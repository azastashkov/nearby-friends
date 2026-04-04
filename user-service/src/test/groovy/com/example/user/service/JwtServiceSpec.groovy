package com.example.user.service

import spock.lang.Specification
import spock.lang.Subject

class JwtServiceSpec extends Specification {

    @Subject
    JwtService jwtService

    def setup() {
        jwtService = new JwtService(
            'a-test-secret-key-that-is-at-least-32-bytes-long-for-hmac-sha256-!!'
        )
    }

    def 'should generate a non-null token for valid user'() {
        when:
        def token = jwtService.generateToken(
            UUID.fromString('550e8400-e29b-41d4-a716-446655440000'), 'alice')

        then:
        token != null
        !token.isBlank()
    }

    def 'should validate a freshly generated token and return correct claims'() {
        given:
        def userId = UUID.fromString('550e8400-e29b-41d4-a716-446655440000')
        def username = 'alice'
        def token = jwtService.generateToken(userId, username)

        when:
        def claims = jwtService.validateToken(token)

        then:
        claims.getSubject() == userId.toString()
        claims.get('username', String) == username
    }

    def 'should reject a tampered token'() {
        given:
        def token = jwtService.generateToken(UUID.randomUUID(), 'bob')
        def tampered = token + 'x'

        when:
        jwtService.validateToken(tampered)

        then:
        thrown(io.jsonwebtoken.JwtException)
    }

    def 'should reject a token signed with a different secret'() {
        given:
        def otherService = new JwtService(
            'another-secret-that-is-also-at-least-32-bytes-long-for-hmac-sha!!')
        def token = otherService.generateToken(UUID.randomUUID(), 'eve')

        when:
        jwtService.validateToken(token)

        then:
        thrown(io.jsonwebtoken.JwtException)
    }
}
