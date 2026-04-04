# Nearby Friends Service Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a real-time nearby friends microservices system with WebSocket-based location sharing, Redis pub/sub with consistent hashing, and full Docker Compose deployment with monitoring.

**Architecture:** 4 Spring Boot microservices (API Gateway, User Service, Location Service, WebSocket Service) behind Nginx. Redis for location cache (TTL) and pub/sub (3-node cluster with consistent hashing via ZooKeeper). PostgreSQL for users/friendships, Cassandra for location history. 2 instances of each application service for HA.

**Tech Stack:** Java 21, Spring Boot 3.x, Gradle (Groovy DSL), Spock tests, PostgreSQL, Cassandra, Redis, ZooKeeper, Spring Cloud Gateway, Nginx, JWT, Prometheus + Grafana, Docker Compose.

**Spec:** `docs/superpowers/specs/2026-04-04-nearby-friends-design.md`

---

### Task 1: Project Scaffolding

**Files:**
- Create: `settings.gradle`
- Create: `build.gradle`
- Create: `api-gateway/build.gradle`
- Create: `user-service/build.gradle`
- Create: `location-service/build.gradle`
- Create: `websocket-service/build.gradle`
- Create: `load-client/build.gradle`

- [ ] **Step 1: Create directory structure**

```bash
cd /Users/azastashkov/projects/github/nearby-friends

mkdir -p api-gateway/src/main/java/com/example/gateway/config
mkdir -p api-gateway/src/main/resources
mkdir -p api-gateway/src/test/groovy/com/example/gateway

mkdir -p user-service/src/main/java/com/example/user/{controller,service,model,repository,config}
mkdir -p user-service/src/main/resources
mkdir -p user-service/src/test/groovy/com/example/user/service

mkdir -p location-service/src/main/java/com/example/location/{controller,service,model,repository,config}
mkdir -p location-service/src/main/resources
mkdir -p location-service/src/test/groovy/com/example/location/service

mkdir -p websocket-service/src/main/java/com/example/websocket/{config,handler,service,dto}
mkdir -p websocket-service/src/main/resources
mkdir -p websocket-service/src/test/groovy/com/example/websocket/service

mkdir -p load-client/src/main/java/com/example/loadtest
mkdir -p load-client/src/main/resources

mkdir -p nginx
mkdir -p monitoring/prometheus
mkdir -p monitoring/grafana/provisioning/datasources
mkdir -p monitoring/grafana/provisioning/dashboards
mkdir -p monitoring/grafana/dashboards
mkdir -p docs
```

- [ ] **Step 2: Write settings.gradle**

```groovy
rootProject.name = 'nearby-friends'

include 'api-gateway'
include 'user-service'
include 'location-service'
include 'websocket-service'
include 'load-client'
```

- [ ] **Step 3: Write root build.gradle**

```groovy
plugins {
    id 'java'
    id 'org.springframework.boot' version '3.4.4' apply false
    id 'io.spring.dependency-management' version '1.1.7' apply false
    id 'groovy'
}

allprojects {
    group = 'com.example'
    version = '0.0.1-SNAPSHOT'

    repositories {
        mavenCentral()
    }
}

subprojects {
    apply plugin: 'java'
    apply plugin: 'groovy'
    apply plugin: 'org.springframework.boot'
    apply plugin: 'io.spring.dependency-management'

    java {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    dependencies {
        implementation 'org.springframework.boot:spring-boot-starter-actuator'
        implementation 'io.micrometer:micrometer-registry-prometheus'

        testImplementation 'org.springframework.boot:spring-boot-starter-test'
        testImplementation 'org.apache.groovy:groovy-all:4.0.27'
        testImplementation platform('org.spockframework:spock-bom:2.4-M4-groovy-4.0')
        testImplementation 'org.spockframework:spock-core'
        testImplementation 'org.spockframework:spock-spring'
    }

    tasks.named('test') {
        useJUnitPlatform()
    }

    sourceSets {
        test {
            groovy {
                srcDirs = ['src/test/groovy']
            }
        }
    }
}
```

- [ ] **Step 4: Write api-gateway/build.gradle**

```groovy
dependencies {
    implementation 'org.springframework.cloud:spring-cloud-starter-gateway'
    implementation 'io.jsonwebtoken:jjwt-api:0.12.6'
    runtimeOnly 'io.jsonwebtoken:jjwt-impl:0.12.6'
    runtimeOnly 'io.jsonwebtoken:jjwt-jackson:0.12.6'
}

dependencyManagement {
    imports {
        mavenBom 'org.springframework.cloud:spring-cloud-dependencies:2024.0.1'
    }
}
```

- [ ] **Step 5: Write user-service/build.gradle**

```groovy
dependencies {
    implementation 'org.springframework.boot:spring-boot-starter-web'
    implementation 'org.springframework.boot:spring-boot-starter-data-jpa'
    implementation 'org.springframework.security:spring-security-crypto'
    implementation 'io.jsonwebtoken:jjwt-api:0.12.6'

    runtimeOnly 'org.postgresql:postgresql'
    runtimeOnly 'io.jsonwebtoken:jjwt-impl:0.12.6'
    runtimeOnly 'io.jsonwebtoken:jjwt-jackson:0.12.6'
}
```

Note: Uses `spring-security-crypto` (not the full starter-security) for `BCryptPasswordEncoder` only, avoiding auto-configured CSRF/session/login-form.

- [ ] **Step 6: Write location-service/build.gradle**

```groovy
dependencies {
    implementation 'org.springframework.boot:spring-boot-starter-web'
    implementation 'org.springframework.boot:spring-boot-starter-data-cassandra'
}
```

- [ ] **Step 7: Write websocket-service/build.gradle**

```groovy
dependencies {
    implementation 'org.springframework.boot:spring-boot-starter-websocket'
    implementation 'org.springframework.boot:spring-boot-starter-webflux'
    implementation 'org.springframework.boot:spring-boot-starter-data-redis'
    implementation 'org.apache.curator:curator-recipes:5.7.1'
    implementation 'com.google.code.gson:gson:2.12.1'
    implementation 'io.jsonwebtoken:jjwt-api:0.12.6'
    runtimeOnly 'io.jsonwebtoken:jjwt-impl:0.12.6'
    runtimeOnly 'io.jsonwebtoken:jjwt-jackson:0.12.6'
}
```

Note: Uses `spring-boot-starter-webflux` for `WebClient` (async HTTP to user-service/location-service). Uses `spring-boot-starter-data-redis` which brings Lettuce (preferred over Jedis for non-blocking pub/sub -- can multiplex subscriptions on a single connection instead of requiring one thread per subscription).

- [ ] **Step 8: Write load-client/build.gradle**

```groovy
dependencies {
    implementation 'org.springframework.boot:spring-boot-starter-web'
    implementation 'org.springframework.boot:spring-boot-starter-webflux'
    implementation 'org.java-websocket:Java-WebSocket:1.6.0'
    implementation 'com.google.code.gson:gson:2.12.1'
}
```

- [ ] **Step 9: Generate Gradle wrapper and verify**

```bash
cd /Users/azastashkov/projects/github/nearby-friends
gradle wrapper --gradle-version 8.10
./gradlew projects
```

Expected: Lists all 5 subprojects.

- [ ] **Step 10: Commit**

```bash
git add settings.gradle build.gradle */build.gradle gradlew gradlew.bat gradle/
git commit -m "chore: scaffold multi-project Gradle build with 5 subprojects"
```

---

### Task 2: User Service — Tests (RED)

**Files:**
- Create: `user-service/src/test/groovy/com/example/user/service/JwtServiceSpec.groovy`
- Create: `user-service/src/test/groovy/com/example/user/service/UserServiceSpec.groovy`

- [ ] **Step 1: Write JwtServiceSpec**

```groovy
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
            UUID.fromString('550e8400-e29b-41d4-a716-446655440000'),
            'alice'
        )

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
            'another-secret-that-is-also-at-least-32-bytes-long-for-hmac-sha!!'
        )
        def token = otherService.generateToken(UUID.randomUUID(), 'eve')

        when:
        jwtService.validateToken(token)

        then:
        thrown(io.jsonwebtoken.JwtException)
    }
}
```

- [ ] **Step 2: Write UserServiceSpec**

```groovy
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
        userRepository.save(_ as User) >> { User u -> u }

        when:
        def user = userService.register(username, rawPassword)

        then:
        user.username == username
        user.passwordHash != rawPassword
        passwordEncoder.matches(rawPassword, user.passwordHash)
        1 * userRepository.save(_ as User)
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
```

- [ ] **Step 3: Verify tests fail (classes don't exist yet)**

```bash
./gradlew :user-service:test
```

Expected: Compilation failure — `JwtService`, `UserService`, `User`, `UserRepository` not found.

---

### Task 3: User Service — Implementation (GREEN)

**Files:**
- Create: `user-service/src/main/java/com/example/user/UserServiceApplication.java`
- Create: `user-service/src/main/java/com/example/user/model/User.java`
- Create: `user-service/src/main/java/com/example/user/model/FriendshipId.java`
- Create: `user-service/src/main/java/com/example/user/model/Friendship.java`
- Create: `user-service/src/main/java/com/example/user/repository/UserRepository.java`
- Create: `user-service/src/main/java/com/example/user/repository/FriendshipRepository.java`
- Create: `user-service/src/main/java/com/example/user/service/JwtService.java`
- Create: `user-service/src/main/java/com/example/user/service/UserService.java`
- Create: `user-service/src/main/java/com/example/user/service/FriendshipService.java`
- Create: `user-service/src/main/java/com/example/user/controller/UserController.java`
- Create: `user-service/src/main/java/com/example/user/controller/FriendshipController.java`
- Create: `user-service/src/main/java/com/example/user/config/SecurityConfig.java`
- Create: `user-service/src/main/resources/application.yml`
- Create: `user-service/src/main/resources/schema.sql`

- [ ] **Step 1: Write User entity**

```java
package com.example.user.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true)
    private String username;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
```

- [ ] **Step 2: Write FriendshipId and Friendship entities**

FriendshipId.java:
```java
package com.example.user.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Embeddable
public class FriendshipId implements Serializable {

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "friend_id")
    private UUID friendId;

    public FriendshipId() {}
    public FriendshipId(UUID userId, UUID friendId) {
        this.userId = userId;
        this.friendId = friendId;
    }

    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }
    public UUID getFriendId() { return friendId; }
    public void setFriendId(UUID friendId) { this.friendId = friendId; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof FriendshipId that)) return false;
        return Objects.equals(userId, that.userId) && Objects.equals(friendId, that.friendId);
    }

    @Override
    public int hashCode() { return Objects.hash(userId, friendId); }
}
```

Friendship.java:
```java
package com.example.user.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "friendships")
public class Friendship {

    @EmbeddedId
    private FriendshipId id;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public Friendship() {}
    public Friendship(FriendshipId id) { this.id = id; }

    public FriendshipId getId() { return id; }
    public void setId(FriendshipId id) { this.id = id; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
```

- [ ] **Step 3: Write repositories**

UserRepository.java:
```java
package com.example.user.repository;

import com.example.user.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByUsername(String username);
}
```

FriendshipRepository.java:
```java
package com.example.user.repository;

import com.example.user.model.Friendship;
import com.example.user.model.FriendshipId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.UUID;

public interface FriendshipRepository extends JpaRepository<Friendship, FriendshipId> {

    @Query("SELECT f FROM Friendship f WHERE f.id.userId = :userId")
    List<Friendship> findAllByUserId(@Param("userId") UUID userId);

    @Modifying
    @Query("DELETE FROM Friendship f WHERE " +
           "(f.id.userId = :userId AND f.id.friendId = :friendId) OR " +
           "(f.id.userId = :friendId AND f.id.friendId = :userId)")
    void deleteBidirectional(@Param("userId") UUID userId, @Param("friendId") UUID friendId);
}
```

- [ ] **Step 4: Write JwtService**

```java
package com.example.user.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

@Service
public class JwtService {

    private final SecretKey signingKey;
    private static final Duration TOKEN_VALIDITY = Duration.ofHours(24);

    public JwtService(@Value("${jwt.secret}") String secret) {
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public String generateToken(UUID userId, String username) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(userId.toString())
                .claim("username", username)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(TOKEN_VALIDITY)))
                .signWith(signingKey)
                .compact();
    }

    public Claims validateToken(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
```

- [ ] **Step 5: Write UserService**

```java
package com.example.user.service;

import com.example.user.model.User;
import com.example.user.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import java.util.UUID;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public UserService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    public User register(String username, String rawPassword) {
        userRepository.findByUsername(username).ifPresent(u -> {
            throw new IllegalArgumentException("Username already exists: " + username);
        });
        User user = new User();
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        return userRepository.save(user);
    }

    public Map<String, Object> login(String username, String rawPassword) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + username));
        if (!passwordEncoder.matches(rawPassword, user.getPasswordHash())) {
            throw new IllegalArgumentException("Invalid password");
        }
        String token = jwtService.generateToken(user.getId(), user.getUsername());
        return Map.of("userId", user.getId(), "token", token);
    }

    public User getProfile(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));
    }
}
```

- [ ] **Step 6: Write FriendshipService**

```java
package com.example.user.service;

import com.example.user.model.Friendship;
import com.example.user.model.FriendshipId;
import com.example.user.repository.FriendshipRepository;
import com.example.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.UUID;

@Service
public class FriendshipService {

    private final FriendshipRepository friendshipRepository;
    private final UserRepository userRepository;

    public FriendshipService(FriendshipRepository friendshipRepository,
                             UserRepository userRepository) {
        this.friendshipRepository = friendshipRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public void addFriendship(UUID userId, UUID friendId) {
        userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));
        userRepository.findById(friendId)
                .orElseThrow(() -> new IllegalArgumentException("Friend not found: " + friendId));
        friendshipRepository.save(new Friendship(new FriendshipId(userId, friendId)));
        friendshipRepository.save(new Friendship(new FriendshipId(friendId, userId)));
    }

    @Transactional
    public void removeFriendship(UUID userId, UUID friendId) {
        friendshipRepository.deleteBidirectional(userId, friendId);
    }

    public List<UUID> listFriends(UUID userId) {
        return friendshipRepository.findAllByUserId(userId).stream()
                .map(f -> f.getId().getFriendId())
                .toList();
    }
}
```

- [ ] **Step 7: Write controllers**

UserController.java:
```java
package com.example.user.controller;

import com.example.user.model.User;
import com.example.user.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("/register")
    public ResponseEntity<Map<String, Object>> register(@RequestBody Map<String, String> request) {
        User user = userService.register(request.get("username"), request.get("password"));
        return ResponseEntity.ok(Map.of("id", user.getId(), "username", user.getUsername()));
    }

    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@RequestBody Map<String, String> request) {
        Map<String, Object> result = userService.login(request.get("username"), request.get("password"));
        return ResponseEntity.ok(result);
    }

    @GetMapping("/{userId}")
    public ResponseEntity<Map<String, Object>> getProfile(@PathVariable UUID userId) {
        User user = userService.getProfile(userId);
        return ResponseEntity.ok(Map.of(
                "id", user.getId(),
                "username", user.getUsername(),
                "createdAt", user.getCreatedAt().toString()
        ));
    }
}
```

FriendshipController.java:
```java
package com.example.user.controller;

import com.example.user.service.FriendshipService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/users/{userId}/friends")
public class FriendshipController {

    private final FriendshipService friendshipService;

    public FriendshipController(FriendshipService friendshipService) {
        this.friendshipService = friendshipService;
    }

    @PostMapping("/{friendId}")
    public ResponseEntity<Void> addFriend(@PathVariable UUID userId, @PathVariable UUID friendId) {
        friendshipService.addFriendship(userId, friendId);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{friendId}")
    public ResponseEntity<Void> removeFriend(@PathVariable UUID userId, @PathVariable UUID friendId) {
        friendshipService.removeFriendship(userId, friendId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> listFriends(@PathVariable UUID userId) {
        List<UUID> friends = friendshipService.listFriends(userId);
        return ResponseEntity.ok(Map.of("friends", friends));
    }
}
```

- [ ] **Step 8: Write SecurityConfig and application.yml**

SecurityConfig.java:
```java
package com.example.user.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class SecurityConfig {
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
```

application.yml:
```yaml
server:
  port: ${SERVER_PORT:8082}

spring:
  application:
    name: user-service
  datasource:
    url: ${SPRING_DATASOURCE_URL:jdbc:postgresql://localhost:5432/nearby_friends}
    username: ${SPRING_DATASOURCE_USERNAME:postgres}
    password: ${SPRING_DATASOURCE_PASSWORD:postgres}
    driver-class-name: org.postgresql.Driver
  jpa:
    hibernate:
      ddl-auto: ${SPRING_JPA_HIBERNATE_DDL_AUTO:none}
    open-in-view: false
  sql:
    init:
      mode: always
      schema-locations: classpath:schema.sql

jwt:
  secret: ${JWT_SECRET:default-dev-secret-key-must-be-at-least-32-bytes-long!!}

management:
  endpoints:
    web:
      exposure:
        include: health,prometheus
  metrics:
    tags:
      application: user-service
```

schema.sql:
```sql
CREATE TABLE IF NOT EXISTS users (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    username   VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS friendships (
    user_id    UUID NOT NULL REFERENCES users(id),
    friend_id  UUID NOT NULL REFERENCES users(id),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, friend_id)
);
```

- [ ] **Step 9: Write UserServiceApplication**

```java
package com.example.user;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class UserServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(UserServiceApplication.class, args);
    }
}
```

- [ ] **Step 10: Run tests, verify they pass**

```bash
./gradlew :user-service:test
```

Expected: All JwtServiceSpec and UserServiceSpec tests PASS.

- [ ] **Step 11: Commit**

```bash
git add user-service/
git commit -m "feat: implement user service with JWT auth, friendships, and Spock tests"
```

---

### Task 4: Location Service (TDD)

**Files:**
- Create: `location-service/src/test/groovy/com/example/location/service/LocationHistoryServiceSpec.groovy`
- Create: `location-service/src/main/java/com/example/location/LocationServiceApplication.java`
- Create: `location-service/src/main/java/com/example/location/model/LocationHistory.java`
- Create: `location-service/src/main/java/com/example/location/repository/LocationHistoryRepository.java`
- Create: `location-service/src/main/java/com/example/location/service/LocationHistoryService.java`
- Create: `location-service/src/main/java/com/example/location/controller/LocationHistoryController.java`
- Create: `location-service/src/main/java/com/example/location/config/CassandraConfig.java`
- Create: `location-service/src/main/resources/application.yml`

- [ ] **Step 1: Write LocationHistoryServiceSpec**

```groovy
package com.example.location.service

import com.example.location.model.LocationHistory
import com.example.location.repository.LocationHistoryRepository
import spock.lang.Specification
import spock.lang.Subject

import java.time.Instant

class LocationHistoryServiceSpec extends Specification {

    LocationHistoryRepository repository = Mock()

    @Subject
    LocationHistoryService service = new LocationHistoryService(repository)

    def 'save should persist a location history record'() {
        given:
        def userId = UUID.randomUUID()
        def latitude = 37.7749d
        def longitude = -122.4194d

        when:
        service.save(userId, latitude, longitude)

        then:
        1 * repository.save({ LocationHistory lh ->
            lh.userId == userId &&
            lh.latitude == latitude &&
            lh.longitude == longitude &&
            lh.timestamp != null
        })
    }

    def 'queryByTimeRange should return records within the time window'() {
        given:
        def userId = UUID.randomUUID()
        def now = Instant.now()
        def start = now.minusSeconds(3600)
        def end = now

        def record1 = new LocationHistory(userId, now.minusSeconds(1800), 37.77, -122.42)
        def record2 = new LocationHistory(userId, now.minusSeconds(900), 37.78, -122.41)
        repository.findByUserIdAndTimestampBetween(userId, start, end) >> [record1, record2]

        when:
        def results = service.queryByTimeRange(userId, start, end)

        then:
        results.size() == 2
        results[0].latitude == 37.77d
        results[1].latitude == 37.78d
    }

    def 'queryByTimeRange should return empty list when no records exist'() {
        given:
        def userId = UUID.randomUUID()
        def start = Instant.now().minusSeconds(3600)
        def end = Instant.now()
        repository.findByUserIdAndTimestampBetween(userId, start, end) >> []

        when:
        def results = service.queryByTimeRange(userId, start, end)

        then:
        results.isEmpty()
    }
}
```

- [ ] **Step 2: Write LocationHistory model**

```java
package com.example.location.model;

import org.springframework.data.cassandra.core.cql.Ordering;
import org.springframework.data.cassandra.core.cql.PrimaryKeyType;
import org.springframework.data.cassandra.core.mapping.Column;
import org.springframework.data.cassandra.core.mapping.PrimaryKeyColumn;
import org.springframework.data.cassandra.core.mapping.Table;

import java.time.Instant;
import java.util.UUID;

@Table("location_history")
public class LocationHistory {

    @PrimaryKeyColumn(name = "user_id", ordinal = 0, type = PrimaryKeyType.PARTITIONED)
    private UUID userId;

    @PrimaryKeyColumn(name = "timestamp", ordinal = 1, type = PrimaryKeyType.CLUSTERED, ordering = Ordering.DESCENDING)
    private Instant timestamp;

    @Column("latitude")
    private double latitude;

    @Column("longitude")
    private double longitude;

    public LocationHistory() {}

    public LocationHistory(UUID userId, Instant timestamp, double latitude, double longitude) {
        this.userId = userId;
        this.timestamp = timestamp;
        this.latitude = latitude;
        this.longitude = longitude;
    }

    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }
    public Instant getTimestamp() { return timestamp; }
    public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }
    public double getLatitude() { return latitude; }
    public void setLatitude(double latitude) { this.latitude = latitude; }
    public double getLongitude() { return longitude; }
    public void setLongitude(double longitude) { this.longitude = longitude; }
}
```

- [ ] **Step 3: Write LocationHistoryRepository**

```java
package com.example.location.repository;

import com.example.location.model.LocationHistory;
import org.springframework.data.cassandra.repository.CassandraRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface LocationHistoryRepository extends CassandraRepository<LocationHistory, UUID> {
    List<LocationHistory> findByUserIdAndTimestampBetween(UUID userId, Instant start, Instant end);
}
```

- [ ] **Step 4: Write LocationHistoryService**

```java
package com.example.location.service;

import com.example.location.model.LocationHistory;
import com.example.location.repository.LocationHistoryRepository;
import org.springframework.stereotype.Service;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class LocationHistoryService {

    private final LocationHistoryRepository repository;

    public LocationHistoryService(LocationHistoryRepository repository) {
        this.repository = repository;
    }

    public LocationHistory save(UUID userId, double latitude, double longitude) {
        LocationHistory record = new LocationHistory(userId, Instant.now(), latitude, longitude);
        return repository.save(record);
    }

    public List<LocationHistory> queryByTimeRange(UUID userId, Instant start, Instant end) {
        return repository.findByUserIdAndTimestampBetween(userId, start, end);
    }
}
```

- [ ] **Step 5: Write LocationHistoryController**

```java
package com.example.location.controller;

import com.example.location.model.LocationHistory;
import com.example.location.service.LocationHistoryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/locations")
public class LocationHistoryController {

    private final LocationHistoryService service;

    public LocationHistoryController(LocationHistoryService service) {
        this.service = service;
    }

    @PostMapping("/history")
    public ResponseEntity<Void> save(@RequestBody Map<String, Object> request) {
        UUID userId = UUID.fromString((String) request.get("userId"));
        double latitude = ((Number) request.get("latitude")).doubleValue();
        double longitude = ((Number) request.get("longitude")).doubleValue();
        service.save(userId, latitude, longitude);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/history/{userId}")
    public ResponseEntity<List<LocationHistory>> query(
            @PathVariable UUID userId,
            @RequestParam Instant start,
            @RequestParam Instant end) {
        return ResponseEntity.ok(service.queryByTimeRange(userId, start, end));
    }
}
```

- [ ] **Step 6: Write CassandraConfig and application.yml**

CassandraConfig.java — creates keyspace programmatically if not exists:
```java
package com.example.location.config;

import com.datastax.oss.driver.api.core.CqlSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.cassandra.config.AbstractCassandraConfiguration;
import org.springframework.data.cassandra.config.SchemaAction;

@Configuration
public class CassandraConfig extends AbstractCassandraConfiguration {

    @Value("${spring.cassandra.contact-points:cassandra}")
    private String contactPoints;

    @Value("${spring.cassandra.port:9042}")
    private int port;

    @Value("${spring.cassandra.keyspace-name:nearby_friends}")
    private String keyspaceName;

    @Value("${spring.cassandra.local-datacenter:dc1}")
    private String localDatacenter;

    @Override
    protected String getKeyspaceName() { return keyspaceName; }

    @Override
    protected String getContactPoints() { return contactPoints; }

    @Override
    protected int getPort() { return port; }

    @Override
    protected String getLocalDataCenter() { return localDatacenter; }

    @Override
    public SchemaAction getSchemaAction() { return SchemaAction.CREATE_IF_NOT_EXISTS; }

    @Override
    protected java.util.List<String> getStartupScripts() {
        return java.util.List.of(
            "CREATE KEYSPACE IF NOT EXISTS " + keyspaceName +
            " WITH replication = {'class': 'SimpleStrategy', 'replication_factor': 1}"
        );
    }
}
```

application.yml:
```yaml
server:
  port: ${SERVER_PORT:8084}

spring:
  application:
    name: location-service
  cassandra:
    contact-points: ${SPRING_CASSANDRA_CONTACT_POINTS:localhost}
    port: ${SPRING_CASSANDRA_PORT:9042}
    local-datacenter: ${SPRING_CASSANDRA_LOCAL_DATACENTER:dc1}
    keyspace-name: ${SPRING_CASSANDRA_KEYSPACE_NAME:nearby_friends}
    schema-action: ${SPRING_CASSANDRA_SCHEMA_ACTION:create_if_not_exists}

management:
  endpoints:
    web:
      exposure:
        include: health,prometheus
  metrics:
    tags:
      application: location-service
```

- [ ] **Step 7: Write LocationServiceApplication**

```java
package com.example.location;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class LocationServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(LocationServiceApplication.class, args);
    }
}
```

- [ ] **Step 8: Run tests, verify they pass**

```bash
./gradlew :location-service:test
```

Expected: All LocationHistoryServiceSpec tests PASS.

- [ ] **Step 9: Commit**

```bash
git add location-service/
git commit -m "feat: implement location service with Cassandra and Spock tests"
```

---

### Task 5: WebSocket Service — Tests (RED)

**Files:**
- Create: `websocket-service/src/test/groovy/com/example/websocket/service/DistanceCalculatorSpec.groovy`
- Create: `websocket-service/src/test/groovy/com/example/websocket/service/ConsistentHashRingSpec.groovy`
- Create: `websocket-service/src/test/groovy/com/example/websocket/service/NearbyFriendServiceSpec.groovy`

- [ ] **Step 1: Write DistanceCalculatorSpec**

```groovy
package com.example.websocket.service

import spock.lang.Specification

class DistanceCalculatorSpec extends Specification {

    def 'SF to LA should be approximately 347 miles'() {
        when:
        def distance = DistanceCalculator.calculateMiles(37.7749, -122.4194, 34.0522, -118.2437)

        then:
        Math.abs(distance - 347.0) < 5.0
    }

    def 'same point should return 0'() {
        when:
        def distance = DistanceCalculator.calculateMiles(37.7749, -122.4194, 37.7749, -122.4194)

        then:
        distance == 0.0d
    }

    def 'antipodal points should be approximately half Earth circumference'() {
        when:
        def distance = DistanceCalculator.calculateMiles(0.0, 0.0, 0.0, 180.0)

        then:
        Math.abs(distance - 12451.0) < 10.0
    }

    def 'two points 3 miles apart should be within 5 mile radius'() {
        // Two points approximately 3 miles apart in SF
        when:
        def distance = DistanceCalculator.calculateMiles(37.7749, -122.4194, 37.8049, -122.4194)

        then:
        distance > 0.0
        distance <= 5.0
    }

    def 'NY to London should be approximately 3459 miles'() {
        when:
        def distance = DistanceCalculator.calculateMiles(40.7128, -74.0060, 51.5074, -0.1278)

        then:
        Math.abs(distance - 3459.0) < 10.0
    }
}
```

- [ ] **Step 2: Write ConsistentHashRingSpec**

```groovy
package com.example.websocket.service

import spock.lang.Specification

class ConsistentHashRingSpec extends Specification {

    def 'single node should receive all keys'() {
        given:
        def ring = new ConsistentHashRing()
        ring.addNode('redis-1:6379')

        when:
        def results = (1..100).collect { ring.getNode(UUID.randomUUID().toString()) }

        then:
        results.every { it == 'redis-1:6379' }
    }

    def 'three nodes should distribute keys roughly uniformly'() {
        given:
        def ring = new ConsistentHashRing()
        ring.addNode('redis-1:6379')
        ring.addNode('redis-2:6379')
        ring.addNode('redis-3:6379')

        when:
        def counts = [:].withDefault { 0 }
        10000.times {
            def node = ring.getNode(UUID.randomUUID().toString())
            counts[node]++
        }

        then:
        counts.every { _, count -> count > 2500 && count < 4000 }
    }

    def 'same key always returns same node'() {
        given:
        def ring = new ConsistentHashRing()
        ring.addNode('redis-1:6379')
        ring.addNode('redis-2:6379')
        def key = 'test-user-id'

        when:
        def results = (1..100).collect { ring.getNode(key) }

        then:
        results.every { it == results[0] }
    }

    def 'adding a node remaps at most one-third of keys'() {
        given:
        def ring = new ConsistentHashRing()
        ring.addNode('redis-1:6379')
        ring.addNode('redis-2:6379')

        def keys = (1..1000).collect { UUID.randomUUID().toString() }
        def before = keys.collectEntries { [it, ring.getNode(it)] }

        when:
        ring.addNode('redis-3:6379')
        def after = keys.collectEntries { [it, ring.getNode(it)] }

        def changed = keys.count { before[it] != after[it] }

        then:
        changed < 500  // less than 50%
    }

    def 'empty ring should throw IllegalStateException'() {
        given:
        def ring = new ConsistentHashRing()

        when:
        ring.getNode('any-key')

        then:
        thrown(IllegalStateException)
    }

    def 'ring size should be 150 virtual nodes per physical node'() {
        given:
        def ring = new ConsistentHashRing()

        when:
        ring.addNode('redis-1:6379')

        then:
        ring.size() == 150
    }
}
```

- [ ] **Step 3: Write NearbyFriendServiceSpec**

```groovy
package com.example.websocket.service

import com.example.websocket.dto.LocationUpdate
import spock.lang.Specification
import spock.lang.Subject

class NearbyFriendServiceSpec extends Specification {

    LocationCacheService cacheService = Mock()
    PubSubService pubSubService = Mock()

    @Subject
    NearbyFriendService service = new NearbyFriendService(cacheService, pubSubService, 5.0)

    def 'should return only friends within 5 mile radius'() {
        given:
        def userLocation = new LocationUpdate(37.7749, -122.4194, System.currentTimeMillis())
        def nearbyFriend = new LocationUpdate(37.7849, -122.4094, System.currentTimeMillis())
        def farFriend = new LocationUpdate(34.0522, -118.2437, System.currentTimeMillis())
        def friendIds = ['friend-1', 'friend-2']

        cacheService.getLocation('friend-1') >> Optional.of(nearbyFriend)
        cacheService.getLocation('friend-2') >> Optional.of(farFriend)

        when:
        def result = service.computeNearbyFriends(friendIds, userLocation)

        then:
        result.size() == 1
        result[0].friendId() == 'friend-1'
        result[0].distance() <= 5.0
    }

    def 'should handle empty friend list'() {
        given:
        def userLocation = new LocationUpdate(37.7749, -122.4194, System.currentTimeMillis())

        when:
        def result = service.computeNearbyFriends([], userLocation)

        then:
        result.isEmpty()
        0 * cacheService.getLocation(_)
    }

    def 'should skip offline friends with no cached location'() {
        given:
        def userLocation = new LocationUpdate(37.7749, -122.4194, System.currentTimeMillis())
        def friendIds = ['online-friend', 'offline-friend']

        cacheService.getLocation('online-friend') >> Optional.of(
            new LocationUpdate(37.7849, -122.4094, System.currentTimeMillis()))
        cacheService.getLocation('offline-friend') >> Optional.empty()

        when:
        def result = service.computeNearbyFriends(friendIds, userLocation)

        then:
        result.size() == 1
        result[0].friendId() == 'online-friend'
    }

    def 'processLocationUpdate should update cache and publish'() {
        given:
        def userId = 'user-1'
        def update = new LocationUpdate(37.7749, -122.4194, System.currentTimeMillis())

        when:
        service.processLocationUpdate(userId, update)

        then:
        1 * cacheService.updateLocation(userId, update)
        1 * pubSubService.publish(userId, update)
    }

    def 'evaluateFriendUpdate should return nearby friend when within radius'() {
        given:
        def userLocation = new LocationUpdate(37.7749, -122.4194, System.currentTimeMillis())
        def friendLocation = new LocationUpdate(37.7849, -122.4094, System.currentTimeMillis())

        when:
        def result = service.evaluateFriendUpdate(userLocation, 'friend-1', friendLocation)

        then:
        result.isPresent()
        result.get().distance() <= 5.0
    }

    def 'evaluateFriendUpdate should return empty when beyond radius'() {
        given:
        def userLocation = new LocationUpdate(37.7749, -122.4194, System.currentTimeMillis())
        def friendLocation = new LocationUpdate(34.0522, -118.2437, System.currentTimeMillis())

        when:
        def result = service.evaluateFriendUpdate(userLocation, 'friend-1', friendLocation)

        then:
        result.isEmpty()
    }
}
```

- [ ] **Step 4: Verify tests fail**

```bash
./gradlew :websocket-service:test
```

Expected: Compilation failure — classes don't exist yet.

---

### Task 6: WebSocket Service — Implementation (GREEN)

**Files:**
- Create: `websocket-service/src/main/java/com/example/websocket/WebSocketServiceApplication.java`
- Create: `websocket-service/src/main/java/com/example/websocket/dto/LocationUpdate.java`
- Create: `websocket-service/src/main/java/com/example/websocket/service/DistanceCalculator.java`
- Create: `websocket-service/src/main/java/com/example/websocket/service/ConsistentHashRing.java`
- Create: `websocket-service/src/main/java/com/example/websocket/service/ZookeeperService.java`
- Create: `websocket-service/src/main/java/com/example/websocket/service/LocationCacheService.java`
- Create: `websocket-service/src/main/java/com/example/websocket/service/PubSubService.java`
- Create: `websocket-service/src/main/java/com/example/websocket/service/NearbyFriendService.java`
- Create: `websocket-service/src/main/java/com/example/websocket/handler/LocationWebSocketHandler.java`
- Create: `websocket-service/src/main/java/com/example/websocket/config/WebSocketConfig.java`
- Create: `websocket-service/src/main/resources/application.yml`

- [ ] **Step 1: Write LocationUpdate DTO**

```java
package com.example.websocket.dto;

public record LocationUpdate(
    double latitude,
    double longitude,
    long timestamp
) {}
```

- [ ] **Step 2: Write DistanceCalculator**

```java
package com.example.websocket.service;

public final class DistanceCalculator {
    private static final double EARTH_RADIUS_MILES = 3958.8;

    private DistanceCalculator() {}

    public static double calculateMiles(double lat1, double lng1, double lat2, double lng2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                 + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                 * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return EARTH_RADIUS_MILES * c;
    }
}
```

- [ ] **Step 3: Write ConsistentHashRing**

```java
package com.example.websocket.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

public class ConsistentHashRing {
    private static final int VIRTUAL_NODES = 150;
    private final TreeMap<Long, String> ring = new TreeMap<>();
    private final Map<String, List<Long>> nodeHashes = new HashMap<>();

    public void addNode(String address) {
        List<Long> hashes = new ArrayList<>();
        for (int i = 0; i < VIRTUAL_NODES; i++) {
            long hash = hash(address + "#" + i);
            ring.put(hash, address);
            hashes.add(hash);
        }
        nodeHashes.put(address, hashes);
    }

    public void removeNode(String address) {
        List<Long> hashes = nodeHashes.remove(address);
        if (hashes != null) {
            hashes.forEach(ring::remove);
        }
    }

    public String getNode(String key) {
        if (ring.isEmpty()) {
            throw new IllegalStateException("Hash ring is empty");
        }
        long hash = hash(key);
        Map.Entry<Long, String> entry = ring.ceilingEntry(hash);
        return (entry != null) ? entry.getValue() : ring.firstEntry().getValue();
    }

    public int size() {
        return ring.size();
    }

    private long hash(String key) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(key.getBytes(StandardCharsets.UTF_8));
            long hash = 0;
            for (int i = 0; i < 8; i++) {
                hash = (hash << 8) | (digest[i] & 0xFF);
            }
            return hash;
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }
}
```

- [ ] **Step 4: Write ZookeeperService**

```java
package com.example.websocket.service;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.framework.recipes.cache.CuratorCache;
import org.apache.curator.retry.RetryNTimes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class ZookeeperService {

    private static final Logger log = LoggerFactory.getLogger(ZookeeperService.class);
    private final AtomicReference<ConsistentHashRing> ring = new AtomicReference<>(new ConsistentHashRing());
    private final Gson gson = new Gson();

    @Value("${zookeeper.connect-string}")
    private String connectString;

    @Value("${zookeeper.ring-path}")
    private String ringPath;

    @Value("${redis.pubsub.default-nodes}")
    private String defaultNodes;

    private CuratorFramework client;
    private CuratorCache cache;

    @PostConstruct
    public void init() throws Exception {
        client = CuratorFrameworkFactory.newClient(connectString, new RetryNTimes(5, 2000));
        client.start();
        client.blockUntilConnected(30, TimeUnit.SECONDS);

        if (client.checkExists().forPath(ringPath) == null) {
            client.create().creatingParentsIfNeeded()
                    .forPath(ringPath, defaultNodes.getBytes(StandardCharsets.UTF_8));
        }

        byte[] data = client.getData().forPath(ringPath);
        rebuildRing(data);

        cache = CuratorCache.build(client, ringPath);
        cache.listenable().addListener((type, oldData, newData) -> {
            if (newData != null) {
                rebuildRing(newData.getData());
            }
        });
        cache.start();
    }

    private void rebuildRing(byte[] data) {
        List<String> nodes = gson.fromJson(
                new String(data, StandardCharsets.UTF_8),
                new TypeToken<List<String>>() {}.getType());
        ConsistentHashRing newRing = new ConsistentHashRing();
        nodes.forEach(newRing::addNode);
        ring.set(newRing);
        log.info("Rebuilt hash ring with nodes: {}", nodes);
    }

    public ConsistentHashRing getRing() {
        return ring.get();
    }

    @PreDestroy
    public void destroy() {
        if (cache != null) cache.close();
        if (client != null) client.close();
    }
}
```

- [ ] **Step 5: Write LocationCacheService**

```java
package com.example.websocket.service;

import com.example.websocket.dto.LocationUpdate;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;

@Service
public class LocationCacheService {

    private static final String KEY_PREFIX = "location:";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final long ttlSeconds;

    public LocationCacheService(StringRedisTemplate redisTemplate,
                                 ObjectMapper objectMapper,
                                 @Value("${nearby.cache-ttl-seconds}") long ttlSeconds) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.ttlSeconds = ttlSeconds;
    }

    public void updateLocation(String userId, LocationUpdate update) {
        try {
            String json = objectMapper.writeValueAsString(update);
            redisTemplate.opsForValue().set(KEY_PREFIX + userId, json, Duration.ofSeconds(ttlSeconds));
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize location", e);
        }
    }

    public Optional<LocationUpdate> getLocation(String userId) {
        String json = redisTemplate.opsForValue().get(KEY_PREFIX + userId);
        if (json == null) return Optional.empty();
        try {
            return Optional.of(objectMapper.readValue(json, LocationUpdate.class));
        } catch (JsonProcessingException e) {
            return Optional.empty();
        }
    }
}
```

- [ ] **Step 6: Write PubSubService**

```java
package com.example.websocket.service;

import com.example.websocket.dto.LocationUpdate;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;

@Service
public class PubSubService {

    private static final Logger log = LoggerFactory.getLogger(PubSubService.class);
    private static final String CHANNEL_PREFIX = "location:";

    private final ZookeeperService zookeeperService;
    private final ObjectMapper objectMapper;
    private final Map<String, LettuceConnectionFactory> connectionFactories = new ConcurrentHashMap<>();
    private final Map<String, RedisMessageListenerContainer> containers = new ConcurrentHashMap<>();
    private final Map<String, StringRedisTemplate> templates = new ConcurrentHashMap<>();
    private final Map<String, SubscriptionInfo> activeSubscriptions = new ConcurrentHashMap<>();

    @Value("${redis.pubsub.nodes}")
    private List<String> pubsubNodes;

    private record SubscriptionInfo(String nodeAddress, MessageListener listener,
                                     ChannelTopic topic) {}

    public PubSubService(ZookeeperService zookeeperService, ObjectMapper objectMapper) {
        this.zookeeperService = zookeeperService;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void init() {
        for (String nodeAddress : pubsubNodes) {
            String[] parts = nodeAddress.split(":");
            String host = parts[0];
            int port = Integer.parseInt(parts[1]);

            LettuceConnectionFactory factory = new LettuceConnectionFactory(host, port);
            factory.afterPropertiesSet();
            connectionFactories.put(nodeAddress, factory);

            StringRedisTemplate template = new StringRedisTemplate(factory);
            template.afterPropertiesSet();
            templates.put(nodeAddress, template);

            RedisMessageListenerContainer container = new RedisMessageListenerContainer();
            container.setConnectionFactory(factory);
            container.setTaskExecutor(Executors.newFixedThreadPool(20));
            container.afterPropertiesSet();
            container.start();
            containers.put(nodeAddress, container);
        }
    }

    public void publish(String userId, LocationUpdate update) {
        String nodeAddress = zookeeperService.getRing().getNode(userId);
        StringRedisTemplate template = templates.get(nodeAddress);
        try {
            String json = objectMapper.writeValueAsString(update);
            template.convertAndSend(CHANNEL_PREFIX + userId, json);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize location update for publish", e);
        }
    }

    public void subscribe(String userId, MessageListener listener) {
        String nodeAddress = zookeeperService.getRing().getNode(userId);
        ChannelTopic topic = new ChannelTopic(CHANNEL_PREFIX + userId);
        RedisMessageListenerContainer container = containers.get(nodeAddress);
        container.addMessageListener(listener, topic);
        activeSubscriptions.put(userId, new SubscriptionInfo(nodeAddress, listener, topic));
    }

    public void unsubscribe(String userId) {
        SubscriptionInfo info = activeSubscriptions.remove(userId);
        if (info != null) {
            RedisMessageListenerContainer container = containers.get(info.nodeAddress());
            container.removeMessageListener(info.listener(), info.topic());
        }
    }

    @PreDestroy
    public void destroy() {
        containers.values().forEach(RedisMessageListenerContainer::stop);
        connectionFactories.values().forEach(LettuceConnectionFactory::destroy);
    }
}
```

- [ ] **Step 7: Write NearbyFriendService**

```java
package com.example.websocket.service;

import com.example.websocket.dto.LocationUpdate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Service
public class NearbyFriendService {

    public record NearbyFriend(String friendId, double distance, long timestamp) {}

    private final LocationCacheService cacheService;
    private final PubSubService pubSubService;
    private final double radiusMiles;

    public NearbyFriendService(LocationCacheService cacheService,
                                PubSubService pubSubService,
                                @Value("${nearby.radius-miles}") double radiusMiles) {
        this.cacheService = cacheService;
        this.pubSubService = pubSubService;
        this.radiusMiles = radiusMiles;
    }

    public List<NearbyFriend> computeNearbyFriends(List<String> friendIds, LocationUpdate userLocation) {
        return friendIds.stream()
                .map(fid -> cacheService.getLocation(fid)
                        .map(loc -> {
                            double dist = DistanceCalculator.calculateMiles(
                                    userLocation.latitude(), userLocation.longitude(),
                                    loc.latitude(), loc.longitude());
                            return new NearbyFriend(fid, dist, loc.timestamp());
                        })
                        .orElse(null))
                .filter(Objects::nonNull)
                .filter(nf -> nf.distance() <= radiusMiles)
                .toList();
    }

    public void processLocationUpdate(String userId, LocationUpdate update) {
        cacheService.updateLocation(userId, update);
        pubSubService.publish(userId, update);
    }

    public Optional<NearbyFriend> evaluateFriendUpdate(
            LocationUpdate userLocation, String friendId, LocationUpdate friendLocation) {
        double distance = DistanceCalculator.calculateMiles(
                userLocation.latitude(), userLocation.longitude(),
                friendLocation.latitude(), friendLocation.longitude());
        if (distance <= radiusMiles) {
            return Optional.of(new NearbyFriend(friendId, distance, friendLocation.timestamp()));
        }
        return Optional.empty();
    }
}
```

- [ ] **Step 8: Write LocationWebSocketHandler**

```java
package com.example.websocket.handler;

import com.example.websocket.dto.LocationUpdate;
import com.example.websocket.service.NearbyFriendService;
import com.example.websocket.service.PubSubService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import javax.crypto.SecretKey;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class LocationWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(LocationWebSocketHandler.class);

    private final NearbyFriendService nearbyFriendService;
    private final PubSubService pubSubService;
    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final SecretKey jwtKey;

    private final ConcurrentHashMap<String, SessionState> sessions = new ConcurrentHashMap<>();

    private record SessionState(
            String userId,
            ConcurrentWebSocketSessionDecorator session,
            LocationUpdate lastLocation,
            Set<String> subscribedFriends,
            Set<String> nearbyFriendIds
    ) {
        SessionState withLocation(LocationUpdate location) {
            return new SessionState(userId, session, location, subscribedFriends, nearbyFriendIds);
        }
    }

    public LocationWebSocketHandler(NearbyFriendService nearbyFriendService,
                                     PubSubService pubSubService,
                                     WebClient.Builder webClientBuilder,
                                     ObjectMapper objectMapper,
                                     @Value("${jwt.secret}") String jwtSecret,
                                     @Value("${services.user-service-url}") String userServiceUrl) {
        this.nearbyFriendService = nearbyFriendService;
        this.pubSubService = pubSubService;
        this.webClient = webClientBuilder.baseUrl(userServiceUrl).build();
        this.objectMapper = objectMapper;
        this.jwtKey = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession rawSession) throws Exception {
        String token = extractToken(rawSession.getUri());
        if (token == null) {
            rawSession.close(CloseStatus.NOT_ACCEPTABLE);
            return;
        }

        Claims claims;
        try {
            claims = Jwts.parser().verifyWith(jwtKey).build()
                    .parseSignedClaims(token).getPayload();
        } catch (Exception e) {
            rawSession.close(CloseStatus.NOT_ACCEPTABLE);
            return;
        }

        String userId = claims.getSubject();
        var session = new ConcurrentWebSocketSessionDecorator(rawSession, 5000, 65536);
        var state = new SessionState(userId, session, null,
                ConcurrentHashMap.newKeySet(), ConcurrentHashMap.newKeySet());
        sessions.put(rawSession.getId(), state);

        // Fetch friend list and subscribe to their channels
        List<String> friendIds = fetchFriendIds(userId);
        for (String friendId : friendIds) {
            state.subscribedFriends().add(friendId);
            pubSubService.subscribe(friendId, createFriendListener(rawSession.getId(), friendId));
        }

        log.info("WebSocket connected: userId={}, friends={}", userId, friendIds.size());
    }

    @Override
    protected void handleTextMessage(WebSocketSession rawSession, TextMessage message) throws Exception {
        SessionState state = sessions.get(rawSession.getId());
        if (state == null) return;

        LocationUpdate update = objectMapper.readValue(message.getPayload(), LocationUpdate.class);
        sessions.put(rawSession.getId(), state.withLocation(update));

        nearbyFriendService.processLocationUpdate(state.userId(), update);

        // Fire-and-forget: save to location history service
        saveToLocationServiceAsync(state.userId(), update);

        // On first location update, send initial nearby friends list
        if (state.lastLocation() == null) {
            List<String> friendIds = new ArrayList<>(state.subscribedFriends());
            var nearbyFriends = nearbyFriendService.computeNearbyFriends(friendIds, update);
            for (var nf : nearbyFriends) {
                state.nearbyFriendIds().add(nf.friendId());
            }
            String json = objectMapper.writeValueAsString(
                    Map.of("type", "INIT", "nearbyFriends", nearbyFriends));
            state.session().sendMessage(new TextMessage(json));
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession rawSession, CloseStatus status) {
        SessionState state = sessions.remove(rawSession.getId());
        if (state != null) {
            for (String friendId : state.subscribedFriends()) {
                pubSubService.unsubscribe(friendId);
            }
            log.info("WebSocket disconnected: userId={}", state.userId());
        }
    }

    private MessageListener createFriendListener(String sessionId, String friendId) {
        return (Message message, byte[] pattern) -> {
            try {
                SessionState state = sessions.get(sessionId);
                if (state == null || state.lastLocation() == null) return;

                LocationUpdate friendLocation = objectMapper.readValue(
                        message.getBody(), LocationUpdate.class);
                var result = nearbyFriendService.evaluateFriendUpdate(
                        state.lastLocation(), friendId, friendLocation);

                if (result.isPresent()) {
                    state.nearbyFriendIds().add(friendId);
                    String json = objectMapper.writeValueAsString(
                            Map.of("type", "NEARBY", "friendId", friendId,
                                    "distance", result.get().distance(),
                                    "timestamp", result.get().timestamp()));
                    state.session().sendMessage(new TextMessage(json));
                } else if (state.nearbyFriendIds().remove(friendId)) {
                    String json = objectMapper.writeValueAsString(
                            Map.of("type", "REMOVED", "friendId", friendId));
                    state.session().sendMessage(new TextMessage(json));
                }
            } catch (IOException e) {
                log.error("Error processing friend location update", e);
            }
        };
    }

    @SuppressWarnings("unchecked")
    private List<String> fetchFriendIds(String userId) {
        try {
            Map<String, Object> response = webClient.get()
                    .uri("/{userId}/friends", userId)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();
            if (response != null && response.containsKey("friends")) {
                return ((List<?>) response.get("friends")).stream()
                        .map(Object::toString).toList();
            }
        } catch (Exception e) {
            log.error("Failed to fetch friend list for userId={}", userId, e);
        }
        return List.of();
    }

    private void saveToLocationServiceAsync(String userId, LocationUpdate update) {
        // Fire-and-forget - errors logged, don't block real-time flow
        try {
            webClient.post()
                    .uri("/api/locations/history")
                    .bodyValue(Map.of("userId", userId,
                            "latitude", update.latitude(),
                            "longitude", update.longitude()))
                    .retrieve()
                    .bodyToMono(Void.class)
                    .subscribe(null, e -> log.warn("Failed to save location history", e));
        } catch (Exception e) {
            log.warn("Failed to initiate location history save", e);
        }
    }

    private String extractToken(URI uri) {
        if (uri == null || uri.getQuery() == null) return null;
        for (String param : uri.getQuery().split("&")) {
            String[] kv = param.split("=", 2);
            if (kv.length == 2 && "token".equals(kv[0])) return kv[1];
        }
        return null;
    }
}
```

Note: Uses `ConcurrentWebSocketSessionDecorator` for thread-safe sends (pub/sub callbacks arrive on Redis listener threads).

- [ ] **Step 9: Write WebSocketConfig**

```java
package com.example.websocket.config;

import com.example.websocket.handler.LocationWebSocketHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final LocationWebSocketHandler handler;

    public WebSocketConfig(LocationWebSocketHandler handler) {
        this.handler = handler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(handler, "/ws/nearby")
                .setAllowedOrigins("*");
    }
}
```

- [ ] **Step 10: Write application.yml**

```yaml
server:
  port: ${SERVER_PORT:8086}

spring:
  application:
    name: websocket-service
  data:
    redis:
      host: ${REDIS_CACHE_HOST:localhost}
      port: ${REDIS_CACHE_PORT:6379}

redis:
  pubsub:
    nodes:
      - redis-pubsub-1:6379
      - redis-pubsub-2:6379
      - redis-pubsub-3:6379
    default-nodes: '["redis-pubsub-1:6379","redis-pubsub-2:6379","redis-pubsub-3:6379"]'

zookeeper:
  connect-string: ${ZOOKEEPER_CONNECT_STRING:localhost:2181}
  ring-path: /config/pub_sub_ring

jwt:
  secret: ${JWT_SECRET:default-dev-secret-key-must-be-at-least-32-bytes-long!!}

services:
  user-service-url: ${USER_SERVICE_URL:http://localhost:8082/api/users}
  location-service-url: ${LOCATION_SERVICE_URL:http://localhost:8084}

nearby:
  radius-miles: 5.0
  cache-ttl-seconds: 30

management:
  endpoints:
    web:
      exposure:
        include: health,prometheus
  metrics:
    tags:
      application: websocket-service
```

- [ ] **Step 11: Write WebSocketServiceApplication**

```java
package com.example.websocket;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class WebSocketServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(WebSocketServiceApplication.class, args);
    }
}
```

- [ ] **Step 12: Run tests, verify they pass**

```bash
./gradlew :websocket-service:test
```

Expected: All DistanceCalculatorSpec, ConsistentHashRingSpec, NearbyFriendServiceSpec tests PASS.

- [ ] **Step 13: Commit**

```bash
git add websocket-service/
git commit -m "feat: implement websocket service with pub/sub, consistent hashing, and Spock tests"
```

---

### Task 7: API Gateway

**Files:**
- Create: `api-gateway/src/main/java/com/example/gateway/ApiGatewayApplication.java`
- Create: `api-gateway/src/main/java/com/example/gateway/config/JwtAuthFilter.java`
- Create: `api-gateway/src/main/resources/application.yml`

- [ ] **Step 1: Write ApiGatewayApplication**

```java
package com.example.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class ApiGatewayApplication {
    public static void main(String[] args) {
        SpringApplication.run(ApiGatewayApplication.class, args);
    }
}
```

- [ ] **Step 2: Write JwtAuthFilter**

```java
package com.example.gateway.config;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Component
public class JwtAuthFilter implements GlobalFilter, Ordered {

    private final SecretKey signingKey;

    private static final List<String> PUBLIC_PATHS = List.of(
            "/api/users/register",
            "/api/users/login"
    );

    public JwtAuthFilter(@Value("${jwt.secret}") String secret) {
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();

        if (PUBLIC_PATHS.stream().anyMatch(path::startsWith)) {
            return chain.filter(exchange);
        }

        String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }

        String token = authHeader.substring(7);
        try {
            Jwts.parser().verifyWith(signingKey).build().parseSignedClaims(token);
        } catch (Exception e) {
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }

        return chain.filter(exchange);
    }

    @Override
    public int getOrder() {
        return -1;
    }
}
```

- [ ] **Step 3: Write application.yml**

```yaml
server:
  port: ${SERVER_PORT:8080}

spring:
  application:
    name: api-gateway
  cloud:
    gateway:
      routes:
        - id: user-service
          uri: lb://user-service
          predicates:
            - Path=/api/users/**
        - id: location-service
          uri: lb://location-service
          predicates:
            - Path=/api/locations/**
      default-filters:
        - DedupeResponseHeader=Access-Control-Allow-Origin

jwt:
  secret: ${JWT_SECRET:default-dev-secret-key-must-be-at-least-32-bytes-long!!}

management:
  endpoints:
    web:
      exposure:
        include: health,prometheus
  metrics:
    tags:
      application: api-gateway
```

Note: Since we're not using service discovery (Eureka), the gateway routes will use direct URLs configured via environment variables. The `lb://` prefix should be replaced with direct URLs. The docker-compose env vars `USER_SERVICE_URL` and `LOCATION_SERVICE_URL` will configure these. Adjust application.yml to accept these as environment-variable-driven route URIs instead.

- [ ] **Step 4: Commit**

```bash
git add api-gateway/
git commit -m "feat: implement API gateway with JWT auth filter and route config"
```

---

### Task 8: Docker Compose + Infrastructure

**Files:**
- Create: `docker-compose.yml`
- Create: `nginx/nginx.conf`
- Create: `api-gateway/Dockerfile`
- Create: `user-service/Dockerfile`
- Create: `location-service/Dockerfile`
- Create: `websocket-service/Dockerfile`
- Create: `load-client/Dockerfile`
- Create: `monitoring/prometheus/prometheus.yml`
- Create: `monitoring/grafana/provisioning/datasources/datasource.yml`
- Create: `monitoring/grafana/provisioning/dashboards/dashboard.yml`

- [ ] **Step 1: Write Dockerfiles for all services**

Each follows the same multi-stage pattern (build context is repo root):

Template (replace `SERVICE_NAME` for each):
```dockerfile
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace
COPY gradlew gradlew
COPY gradle gradle
COPY build.gradle build.gradle
COPY settings.gradle settings.gradle
COPY SERVICE_NAME SERVICE_NAME
RUN chmod +x gradlew && ./gradlew :SERVICE_NAME:bootJar --no-daemon -x test

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/SERVICE_NAME/build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

Create 5 files: `api-gateway/Dockerfile`, `user-service/Dockerfile`, `location-service/Dockerfile`, `websocket-service/Dockerfile`, `load-client/Dockerfile`.

- [ ] **Step 2: Write nginx/nginx.conf**

```nginx
worker_processes auto;

events {
    worker_connections 2048;
}

http {
    upstream api_gateway {
        server api-gateway-1:8080;
        server api-gateway-2:8080;
    }

    upstream websocket_service {
        ip_hash;
        server websocket-service-1:8080;
        server websocket-service-2:8080;
    }

    server {
        listen 80;
        server_name localhost;

        location /health {
            access_log off;
            return 200 'ok';
            add_header Content-Type text/plain;
        }

        location /api/ {
            proxy_pass http://api_gateway;
            proxy_set_header Host $host;
            proxy_set_header X-Real-IP $remote_addr;
            proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
            proxy_set_header X-Forwarded-Proto $scheme;
            proxy_connect_timeout 10s;
            proxy_read_timeout 30s;
        }

        location /ws/ {
            proxy_pass http://websocket_service;
            proxy_http_version 1.1;
            proxy_set_header Upgrade $http_upgrade;
            proxy_set_header Connection "upgrade";
            proxy_set_header Host $host;
            proxy_set_header X-Real-IP $remote_addr;
            proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
            proxy_set_header X-Forwarded-Proto $scheme;
            proxy_connect_timeout 10s;
            proxy_read_timeout 3600s;
            proxy_send_timeout 3600s;
        }
    }
}
```

- [ ] **Step 3: Write monitoring configs**

prometheus.yml:
```yaml
global:
  scrape_interval: 15s
  evaluation_interval: 15s

scrape_configs:
  - job_name: 'api-gateway'
    metrics_path: '/actuator/prometheus'
    static_configs:
      - targets: ['api-gateway-1:8080', 'api-gateway-2:8080']

  - job_name: 'user-service'
    metrics_path: '/actuator/prometheus'
    static_configs:
      - targets: ['user-service-1:8080', 'user-service-2:8080']

  - job_name: 'location-service'
    metrics_path: '/actuator/prometheus'
    static_configs:
      - targets: ['location-service-1:8080', 'location-service-2:8080']

  - job_name: 'websocket-service'
    metrics_path: '/actuator/prometheus'
    static_configs:
      - targets: ['websocket-service-1:8080', 'websocket-service-2:8080']

  - job_name: 'load-client'
    metrics_path: '/actuator/prometheus'
    scrape_interval: 5s
    static_configs:
      - targets: ['load-client:8080']
```

datasource.yml:
```yaml
apiVersion: 1
datasources:
  - name: Prometheus
    type: prometheus
    access: proxy
    url: http://prometheus:9090
    isDefault: true
    editable: false
```

dashboard.yml:
```yaml
apiVersion: 1
providers:
  - name: 'Nearby Friends'
    orgId: 1
    folder: ''
    type: file
    disableDeletion: false
    editable: true
    options:
      path: /var/lib/grafana/dashboards
      foldersFromFilesStructure: false
```

- [ ] **Step 4: Write docker-compose.yml**

Full docker-compose.yml with all infrastructure, application (2 instances each), and test profile services. See the spec for the complete service list. Key points:
- All app services use `context: .` and `dockerfile: SERVICE_NAME/Dockerfile`
- Nginx healthcheck uses `wget` (alpine doesn't have curl): `test: ["CMD", "wget", "--spider", "-q", "http://localhost:80/health"]`
- Cassandra has `start_period: 60s` due to slow startup
- All app services use `healthcheck` on `/actuator/health`
- `depends_on` with `condition: service_healthy` enforces startup order
- load-client uses `profiles: [test]`
- Named volumes for data persistence

- [ ] **Step 5: Commit**

```bash
git add docker-compose.yml nginx/ monitoring/ */Dockerfile
git commit -m "feat: add Docker Compose, Nginx, Dockerfiles, and monitoring configs"
```

---

### Task 9: Load Test Client

**Files:**
- Create: `load-client/src/main/java/com/example/loadtest/LoadTestApplication.java`
- Create: `load-client/src/main/java/com/example/loadtest/UserSimulator.java`
- Create: `load-client/src/main/java/com/example/loadtest/MetricsReporter.java`
- Create: `load-client/src/main/resources/application.yml`

- [ ] **Step 1: Write MetricsReporter**

```java
package com.example.loadtest;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class MetricsReporter {

    private final AtomicInteger activeConnections;
    private final Counter failedConnections;
    private final Counter updatesSent;
    private final Counter updatesReceived;
    private final Timer latencyTimer;

    public MetricsReporter(MeterRegistry registry) {
        this.activeConnections = registry.gauge("loadtest.connections.active", new AtomicInteger(0));
        this.failedConnections = registry.counter("loadtest.connections.failed");
        this.updatesSent = registry.counter("loadtest.updates.sent");
        this.updatesReceived = registry.counter("loadtest.updates.received");
        this.latencyTimer = Timer.builder("loadtest.latency.seconds")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(registry);
    }

    public void connectionOpened() { activeConnections.incrementAndGet(); }
    public void connectionClosed() { activeConnections.decrementAndGet(); }
    public void connectionFailed() { failedConnections.increment(); }
    public void updateSent() { updatesSent.increment(); }
    public void updateReceived() { updatesReceived.increment(); }
    public void recordLatency(Duration duration) { latencyTimer.record(duration); }
}
```

- [ ] **Step 2: Write UserSimulator**

```java
package com.example.loadtest;

import com.google.gson.Gson;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.*;

public class UserSimulator {

    private static final Logger log = LoggerFactory.getLogger(UserSimulator.class);
    private static final Gson gson = new Gson();

    private final String userId;
    private final String jwtToken;
    private final String wsUrl;
    private final double clusterLat;
    private final double clusterLng;
    private final MetricsReporter metrics;
    private final ScheduledExecutorService scheduler;

    private WebSocketClient wsClient;
    private volatile double currentLat;
    private volatile double currentLng;

    public UserSimulator(String userId, String jwtToken, String wsUrl,
                          double clusterLat, double clusterLng,
                          MetricsReporter metrics,
                          ScheduledExecutorService scheduler) {
        this.userId = userId;
        this.jwtToken = jwtToken;
        this.wsUrl = wsUrl;
        this.clusterLat = clusterLat;
        this.clusterLng = clusterLng;
        this.metrics = metrics;
        this.scheduler = scheduler;
        this.currentLat = clusterLat + (ThreadLocalRandom.current().nextDouble() - 0.5) * 0.06;
        this.currentLng = clusterLng + (ThreadLocalRandom.current().nextDouble() - 0.5) * 0.06;
    }

    public void connect() {
        try {
            URI uri = new URI(wsUrl + "?token=" + jwtToken);
            wsClient = new WebSocketClient(uri) {
                @Override
                public void onOpen(ServerHandshake handshake) {
                    metrics.connectionOpened();
                    sendLocationUpdate();
                    scheduler.scheduleAtFixedRate(
                            UserSimulator.this::sendLocationUpdate, 30, 30, TimeUnit.SECONDS);
                }

                @Override
                public void onMessage(String message) {
                    metrics.updateReceived();
                    try {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> msg = gson.fromJson(message, Map.class);
                        if (msg.containsKey("timestamp")) {
                            long ts = ((Number) msg.get("timestamp")).longValue();
                            long latency = System.currentTimeMillis() - ts;
                            if (latency >= 0) {
                                metrics.recordLatency(Duration.ofMillis(latency));
                            }
                        }
                    } catch (Exception e) {
                        log.debug("Failed to parse latency from message", e);
                    }
                }

                @Override
                public void onClose(int code, String reason, boolean remote) {
                    metrics.connectionClosed();
                    if (remote) {
                        log.warn("Connection closed unexpectedly for user {}: {}", userId, reason);
                    }
                }

                @Override
                public void onError(Exception ex) {
                    metrics.connectionFailed();
                    log.error("WebSocket error for user {}", userId, ex);
                }
            };
            wsClient.connectBlocking(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            metrics.connectionFailed();
            log.error("Failed to connect user {}", userId, e);
        }
    }

    private void sendLocationUpdate() {
        if (wsClient == null || !wsClient.isOpen()) return;

        // Jitter position slightly (simulates movement within cluster)
        currentLat += (ThreadLocalRandom.current().nextDouble() - 0.5) * 0.002;
        currentLng += (ThreadLocalRandom.current().nextDouble() - 0.5) * 0.002;

        String json = gson.toJson(Map.of(
                "latitude", currentLat,
                "longitude", currentLng,
                "timestamp", System.currentTimeMillis()
        ));
        wsClient.send(json);
        metrics.updateSent();
    }

    public void disconnect() {
        if (wsClient != null) wsClient.close();
    }
}
```

- [ ] **Step 3: Write LoadTestApplication**

```java
package com.example.loadtest;

import com.google.gson.Gson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.*;
import java.util.concurrent.*;

@SpringBootApplication
public class LoadTestApplication implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(LoadTestApplication.class);
    private static final Gson gson = new Gson();

    private static final double[][] CLUSTER_CENTERS = {
            {37.7749, -122.4194},  // San Francisco
            {37.8044, -122.2712},  // Oakland
            {37.7849, -122.4094},  // Near SF
            {34.0522, -118.2437},  // Los Angeles
            {34.0622, -118.2337},  // Near LA
            {40.7128, -74.0060},   // New York
            {40.7228, -74.0160},   // Near NY
            {41.8781, -87.6298},   // Chicago
            {47.6062, -122.3321},  // Seattle
            {25.7617, -80.1918},   // Miami
    };

    @Value("${api.base-url}")
    private String apiBaseUrl;

    @Value("${websocket.url}")
    private String wsUrl;

    @Value("${simulated.users:1000}")
    private int simulatedUsers;

    private final MetricsReporter metrics;

    public LoadTestApplication(MetricsReporter metrics) {
        this.metrics = metrics;
    }

    public static void main(String[] args) {
        SpringApplication.run(LoadTestApplication.class, args);
    }

    @Override
    public void run(String... args) throws Exception {
        WebClient webClient = WebClient.builder().baseUrl(apiBaseUrl).build();

        log.info("Phase 1: Registering {} users...", simulatedUsers);
        Map<String, String> userIds = new LinkedHashMap<>();
        for (int i = 0; i < simulatedUsers; i++) {
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> response = webClient.post()
                        .uri("/api/users/register")
                        .bodyValue(Map.of("username", "loadtest_user_" + i, "password", "pass_" + i))
                        .retrieve()
                        .bodyToMono(Map.class)
                        .block();
                userIds.put(response.get("id").toString(), "loadtest_user_" + i);
            } catch (Exception e) {
                log.warn("Failed to register user {}: {}", i, e.getMessage());
            }
        }
        log.info("Registered {} users", userIds.size());

        log.info("Phase 2: Logging in and collecting tokens...");
        Map<String, String> tokens = new LinkedHashMap<>();
        int idx = 0;
        for (var entry : userIds.entrySet()) {
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> response = webClient.post()
                        .uri("/api/users/login")
                        .bodyValue(Map.of("username", entry.getValue(), "password", "pass_" + idx))
                        .retrieve()
                        .bodyToMono(Map.class)
                        .block();
                tokens.put(entry.getKey(), response.get("token").toString());
            } catch (Exception e) {
                log.warn("Failed to login user {}: {}", entry.getValue(), e.getMessage());
            }
            idx++;
        }
        log.info("Collected {} tokens", tokens.size());

        log.info("Phase 3: Creating friendships...");
        List<String> userIdList = new ArrayList<>(userIds.keySet());
        Random random = new Random(42);
        for (int i = 0; i < userIdList.size(); i++) {
            int friendCount = 10 + random.nextInt(11); // 10-20 friends
            for (int j = 0; j < friendCount; j++) {
                int friendIdx = random.nextInt(userIdList.size());
                if (friendIdx == i) continue;
                try {
                    webClient.post()
                            .uri("/api/users/" + userIdList.get(i) + "/friends/" + userIdList.get(friendIdx))
                            .header("Authorization", "Bearer " + tokens.get(userIdList.get(i)))
                            .retrieve()
                            .bodyToMono(Void.class)
                            .block();
                } catch (Exception e) {
                    // Duplicate friendship, ignore
                }
            }
        }
        log.info("Friendships created");

        log.info("Phase 4: Opening {} WebSocket connections...", tokens.size());
        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(50);
        List<UserSimulator> simulators = new CopyOnWriteArrayList<>();
        ExecutorService connectPool = Executors.newFixedThreadPool(50);

        int userIdx = 0;
        for (var entry : tokens.entrySet()) {
            int clusterIdx = userIdx % CLUSTER_CENTERS.length;
            double[] center = CLUSTER_CENTERS[clusterIdx];
            final String uid = entry.getKey();
            final String token = entry.getValue();

            connectPool.submit(() -> {
                UserSimulator sim = new UserSimulator(uid, token, wsUrl,
                        center[0], center[1], metrics, scheduler);
                sim.connect();
                simulators.add(sim);
            });
            userIdx++;
        }

        connectPool.shutdown();
        connectPool.awaitTermination(5, TimeUnit.MINUTES);
        log.info("All connections established. Load test running. Updates sent every 30s.");

        // Keep running indefinitely (container stays up for monitoring)
        Thread.currentThread().join();
    }
}
```

- [ ] **Step 4: Write application.yml**

```yaml
server:
  port: 8080

api:
  base-url: ${API_BASE_URL:http://localhost}

websocket:
  url: ${WEBSOCKET_URL:ws://localhost/ws/nearby}

simulated:
  users: ${SIMULATED_USERS:1000}

management:
  endpoints:
    web:
      exposure:
        include: health,prometheus
  metrics:
    tags:
      application: load-client
```

- [ ] **Step 5: Commit**

```bash
git add load-client/
git commit -m "feat: implement load test client with 1000 simulated users"
```

---

### Task 10: Grafana Dashboard

**Files:**
- Create: `monitoring/grafana/dashboards/nearby-friends.json`

- [ ] **Step 1: Write Grafana dashboard JSON**

Complete dashboard with 6 rows and 14 panels:
- Row 1 — WebSocket Connections: active connections gauge, connect/disconnect rate
- Row 2 — Location Updates: published/sec and received/sec
- Row 3 — Pub/Sub Traffic: messages per Redis node, active channels
- Row 4 — Latency: propagation latency percentiles, heatmap
- Row 5 — JVM Metrics: heap usage, GC pauses, thread counts per service
- Row 6 — Load Test: active simulated users, sent/received ratio, latency percentiles

Dashboard UID: `nearby-friends-main`, auto-refresh 10s, default range last 30m.

- [ ] **Step 2: Commit**

```bash
git add monitoring/grafana/dashboards/
git commit -m "feat: add pre-provisioned Grafana dashboard"
```

---

### Task 11: Draw.io Diagram + README

**Files:**
- Create: `docs/nearby-friends.drawio`
- Create: `README.md`

- [ ] **Step 1: Write draw.io components diagram**

XML diagram showing all services (color-coded):
- Blue: application services (API Gateway x2, User Service x2, Location Service x2, WebSocket Service x2)
- Green: databases (PostgreSQL, Cassandra, Redis Cache, Redis Pub/Sub x3)
- Orange: infrastructure (Nginx, ZooKeeper, Prometheus, Grafana, ZooNavigator)
- Arrows with labels: REST, WebSocket, pub/sub, cache read/write, scrape

- [ ] **Step 2: Write README.md**

Sections: Project Overview, Architecture, Tech Stack, Prerequisites, How to Build (`./gradlew build`), How to Run (`docker compose up -d`), How to Run Load Tests (`docker compose --profile test up load-client`), Accessing UIs (Grafana at :3000, ZooNavigator at :9000, Prometheus at :9090), API Documentation (endpoint tables), WebSocket Protocol (message formats).

- [ ] **Step 3: Commit**

```bash
git add docs/ README.md
git commit -m "docs: add components diagram and README"
```

---

### Task 12: Build, Deploy, and Load Test

- [ ] **Step 1: Build all services locally**

```bash
./gradlew build
```

Expected: All projects compile, all unit tests pass.

- [ ] **Step 2: Start infrastructure and application services**

```bash
docker compose up -d
```

Wait for all services to be healthy. Check with:
```bash
docker compose ps
```

- [ ] **Step 3: Verify service health endpoints**

```bash
curl http://localhost:8080/actuator/health   # api-gateway-1
curl http://localhost:8082/actuator/health   # user-service-1
curl http://localhost:8084/actuator/health   # location-service-1
curl http://localhost:8086/actuator/health   # websocket-service-1
```

- [ ] **Step 4: Smoke test the APIs**

```bash
# Register a user
curl -X POST http://localhost/api/users/register \
  -H "Content-Type: application/json" \
  -d '{"username":"testuser","password":"testpass"}'

# Login
curl -X POST http://localhost/api/users/login \
  -H "Content-Type: application/json" \
  -d '{"username":"testuser","password":"testpass"}'
```

- [ ] **Step 5: Verify monitoring is working**

- Open Grafana at http://localhost:3000 (admin/admin)
- Check Prometheus at http://localhost:9090 — verify targets are UP
- Open ZooNavigator at http://localhost:9000 — verify `/config/pub_sub_ring` exists

- [ ] **Step 6: Run load test**

```bash
docker compose --profile test up load-client
```

- [ ] **Step 7: Verify load test metrics in Grafana**

Open the "Nearby Friends" dashboard. Check:
- Active WebSocket connections should ramp up to ~1000
- Location updates sent/received should show steady throughput
- Latency p50/p95/p99 should be visible
- No error spikes

- [ ] **Step 8: Fix any issues found**

If any service fails, check logs:
```bash
docker compose logs SERVICE_NAME
```

Common issues to watch for:
- Cassandra keyspace not created → verify CassandraConfig startup script
- Redis pub/sub connection refused → verify docker-compose networking
- WebSocket 401 → verify JWT secret matches across services
- Nginx 502 → verify service health and depends_on order

- [ ] **Step 9: Final commit after fixes**

```bash
git add -A
git commit -m "fix: resolve issues found during load testing"
```

---

## Verification

After completing all tasks:

1. `./gradlew test` — all unit tests pass
2. `docker compose up -d` — all services start and reach healthy state
3. `docker compose --profile test up load-client` — load test runs, connections ramp to 1000
4. Grafana dashboard shows live metrics
5. ZooNavigator shows the pub/sub hash ring
6. Prometheus shows all service targets as UP
