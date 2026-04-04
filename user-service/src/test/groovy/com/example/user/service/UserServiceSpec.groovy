package com.example.user.service

import com.example.user.model.User
import com.example.user.repository.UserRepository
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import spock.lang.Specification
import spock.lang.Subject

class UserServiceSpec extends Specification {

    UserRepository userRepository = Mock()
    PasswordEncoder passwordEncoder = new BCryptPasswordEncoder()
    JwtService jwtService = Mock()

    @Subject
    UserService userService = new UserService(userRepository, passwordEncoder, jwtService)

    def 'register should hash password and save user'() {
        given:
        def username = 'alice'
        def rawPassword = 'secret123'
        userRepository.findByUsername(username) >> Optional.empty()

        when:
        def user = userService.register(username, rawPassword)

        then:
        1 * userRepository.save(_ as User) >> { User u -> u }
        user.username == username
        user.passwordHash != rawPassword
        passwordEncoder.matches(rawPassword, user.passwordHash)
    }

    def 'register should throw when username already exists'() {
        given:
        def existing = new User()
        existing.username = 'alice'
        userRepository.findByUsername('alice') >> Optional.of(existing)

        when:
        userService.register('alice', 'pass')

        then:
        thrown(IllegalArgumentException)
    }

    def 'login should return userId and JWT for valid credentials'() {
        given:
        def userId = UUID.randomUUID()
        def username = 'alice'
        def rawPassword = 'secret123'
        def hashedPassword = passwordEncoder.encode(rawPassword)
        def user = new User()
        user.id = userId
        user.username = username
        user.passwordHash = hashedPassword

        userRepository.findByUsername(username) >> Optional.of(user)
        jwtService.generateToken(userId, username) >> 'jwt-token-value'

        when:
        def result = userService.login(username, rawPassword)

        then:
        result.get('userId') == userId
        result.get('token') == 'jwt-token-value'
    }

    def 'login should throw for non-existent user'() {
        given:
        userRepository.findByUsername('ghost') >> Optional.empty()

        when:
        userService.login('ghost', 'pass')

        then:
        thrown(IllegalArgumentException)
    }

    def 'login should throw for wrong password'() {
        given:
        def user = new User()
        user.username = 'alice'
        user.passwordHash = passwordEncoder.encode('correctPassword')
        userRepository.findByUsername('alice') >> Optional.of(user)

        when:
        userService.login('alice', 'wrongPassword')

        then:
        thrown(IllegalArgumentException)
    }
}
