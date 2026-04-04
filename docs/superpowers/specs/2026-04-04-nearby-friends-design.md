# Nearby Friends Service — Design Specification

## Context

Build a real-time "nearby friends" service that shows users which of their friends are geographically close (within 5 miles). The system must deliver low-latency location updates every 30 seconds via persistent WebSocket connections, use Redis pub/sub with consistent hashing for scalable message fan-out, and run as a full microservices architecture with HA (2 instances per service).

## Technology Stack

- **Language:** Java 21
- **Framework:** Spring Boot 3.x
- **Build:** Gradle (Groovy DSL), multi-project
- **Tests:** Groovy (Spock framework)
- **User DB:** PostgreSQL
- **Location History DB:** Cassandra
- **Cache:** Redis (location cache with TTL)
- **Pub/Sub:** Redis pub/sub cluster (3 nodes)
- **Service Discovery:** ZooKeeper (consistent hashing ring)
- **API Gateway:** Spring Cloud Gateway
- **Load Balancer:** Nginx
- **Auth:** JWT tokens
- **Monitoring:** Prometheus + Grafana
- **Containerization:** Docker Compose

## Service Architecture

### 1. Nginx Load Balancer
Entry point for all traffic.
- `/api/**` → API Gateway upstream (round-robin across 2 instances)
- `/ws/**` → WebSocket Service upstream (IP hash for sticky sessions, 2 instances)

### 2. API Gateway (Spring Cloud Gateway)
Stateless gateway that routes REST traffic to downstream services.
- Routes `/api/users/**` → User Service
- Routes `/api/locations/**` → Location Service
- JWT validation filter on all routes
- 2 instances for HA

### 3. User Service
Manages users and friendships. Issues JWT tokens.
- **Endpoints:**
  - `POST /api/users/register` — register a new user
  - `POST /api/users/login` — authenticate, return JWT
  - `GET /api/users/{userId}/friends` — list friends
  - `POST /api/users/{userId}/friends/{friendId}` — add friendship (bidirectional)
  - `DELETE /api/users/{userId}/friends/{friendId}` — remove friendship
  - `GET /api/users/{userId}` — get user profile
- **Database:** PostgreSQL
  - `users` table: id (UUID), username, password_hash, created_at
  - `friendships` table: user_id, friend_id, created_at (composite PK, bidirectional rows)
- **JWT:** signs tokens with a shared secret; tokens contain userId and username
- 2 instances for HA

### 4. Location Service
Stores and queries historical location data.
- **Endpoints:**
  - `POST /api/locations/history` — save a location history record
  - `GET /api/locations/history/{userId}?start=&end=` — query location history by time range
- **Database:** Cassandra
  - `location_history` table: user_id (partition key), timestamp (clustering key DESC), latitude, longitude
- 2 instances for HA

### 5. WebSocket Service
Stateful service handling real-time location updates.
- **WebSocket endpoint:** `/ws/nearby`
- **Responsibilities:**
  1. Accept WebSocket connections (JWT validated at handshake)
  2. On connect: fetch friend list from User Service, read friends' locations from Redis cache, compute distances, send nearby friends list to client
  3. Subscribe to each online friend's pub/sub channel
  4. On location update from client:
     a. Update Redis location cache (`location:{userId}`, TTL 30s)
     b. Publish to user's Redis pub/sub channel (node selected by consistent hash)
     c. Async POST to Location Service for history persistence
  5. On pub/sub message (friend location update):
     a. Compute Haversine distance
     b. If ≤ 5 miles: send `{friendId, distance, timestamp}` to client via WebSocket
     c. If > 5 miles and was previously nearby: send removal notification
  6. On disconnect: unsubscribe from all friend channels, clean up
- 2 instances for HA (Nginx IP hash for sticky sessions)

## Redis Location Cache

- **Instance:** Dedicated Redis instance (separate from pub/sub)
- **Key format:** `location:{userId}`
- **Value:** JSON `{"latitude": double, "longitude": double, "timestamp": long}`
- **TTL:** 30 seconds (refreshed on each update)
- When TTL expires, the user is considered offline

## Redis Pub/Sub Cluster + Consistent Hashing

- **3 Redis instances** dedicated to pub/sub (redis-pubsub-1, redis-pubsub-2, redis-pubsub-3)
- **ZooKeeper** stores hash ring at `/config/pub_sub_ring`
  - Value: JSON array of server addresses, e.g. `["redis-pubsub-1:6379", "redis-pubsub-2:6379", "redis-pubsub-3:6379"]`
- **Consistent hashing:** `hash(userId) mod virtualNodes → Redis pub/sub node`
  - Each physical node gets ~150 virtual nodes for uniform distribution
- **Channel naming:** `location:{userId}`
- **ZooKeeper watch:** WebSocket Service watches the ring key; on change, rebuilds local hash ring and re-subscribes affected channels

## Distance Calculation

- **Algorithm:** Haversine formula
- **Radius:** Earth radius = 3,958.8 miles
- **Threshold:** 5 miles
- **Input:** two (latitude, longitude) pairs
- **Output:** distance in miles

## Authentication (JWT)

- User Service generates JWT on login with claims: `sub` (userId), `username`, `iat`, `exp`
- Shared secret configured via environment variable `JWT_SECRET`
- API Gateway validates JWT on REST routes
- WebSocket Service validates JWT from query parameter during handshake (`/ws/nearby?token=...`)
- Token expiry: 24 hours

## Data Flow: Location Update

```
Client
  │ WebSocket: {"lat": 37.7749, "lng": -122.4194}
  ▼
WebSocket Service
  ├──► Redis Cache: SET location:{userId} {lat, lng, ts} EX 30
  ├──► Redis Pub/Sub: PUBLISH location:{userId} {lat, lng, ts}
  │         (node selected by consistent hash of userId)
  └──► Location Service: POST /api/locations/history (async)
                │
                ▼
           Cassandra

Meanwhile, on another WebSocket Service instance:
  Friend's WS handler ◄── SUBSCRIBE location:{userId}
       │
       ├── Compute Haversine distance
       └── If ≤ 5mi → push to friend's WebSocket
```

## Data Flow: Client Initialization

```
Client connects to /ws/nearby?token=JWT
  │
  ▼
WebSocket Service
  ├──► Validate JWT
  ├──► GET User Service: /api/users/{userId}/friends
  ├──► For each friend:
  │      ├── GET Redis Cache: location:{friendId}
  │      ├── If exists: compute distance
  │      └── If ≤ 5mi: add to nearby list
  ├──► Send nearby friends list to client
  └──► Subscribe to all friends' pub/sub channels
```

## Project Structure

```
nearby-friends/
├── docker-compose.yml
├── settings.gradle
├── nginx/
│   └── nginx.conf
├── api-gateway/
│   ├── build.gradle
│   ├── Dockerfile
│   └── src/main/java/com/example/gateway/
│       ├── ApiGatewayApplication.java
│       └── config/
│           ├── GatewayConfig.java
│           └── JwtAuthFilter.java
├── user-service/
│   ├── build.gradle
│   ├── Dockerfile
│   └── src/
│       ├── main/java/com/example/user/
│       │   ├── UserServiceApplication.java
│       │   ├── controller/UserController.java
│       │   ├── controller/FriendshipController.java
│       │   ├── service/UserService.java
│       │   ├── service/JwtService.java
│       │   ├── model/User.java
│       │   ├── model/Friendship.java
│       │   ├── repository/UserRepository.java
│       │   └── repository/FriendshipRepository.java
│       ├── main/resources/
│       │   ├── application.yml
│       │   └── schema.sql
│       └── test/groovy/com/example/user/
│           ├── service/UserServiceSpec.groovy
│           └── service/JwtServiceSpec.groovy
├── location-service/
│   ├── build.gradle
│   ├── Dockerfile
│   └── src/
│       ├── main/java/com/example/location/
│       │   ├── LocationServiceApplication.java
│       │   ├── controller/LocationHistoryController.java
│       │   ├── service/LocationHistoryService.java
│       │   ├── model/LocationHistory.java
│       │   └── repository/LocationHistoryRepository.java
│       ├── main/resources/application.yml
│       └── test/groovy/com/example/location/
│           └── service/LocationHistoryServiceSpec.groovy
├── websocket-service/
│   ├── build.gradle
│   ├── Dockerfile
│   └── src/
│       ├── main/java/com/example/websocket/
│       │   ├── WebSocketServiceApplication.java
│       │   ├── config/WebSocketConfig.java
│       │   ├── handler/LocationWebSocketHandler.java
│       │   ├── service/NearbyFriendService.java
│       │   ├── service/LocationCacheService.java
│       │   ├── service/PubSubService.java
│       │   ├── service/ConsistentHashRing.java
│       │   ├── service/ZookeeperService.java
│       │   ├── service/DistanceCalculator.java
│       │   └── dto/LocationUpdate.java
│       ├── main/resources/application.yml
│       └── test/groovy/com/example/websocket/
│           ├── service/DistanceCalculatorSpec.groovy
│           ├── service/ConsistentHashRingSpec.groovy
│           └── service/NearbyFriendServiceSpec.groovy
├── load-client/
│   ├── build.gradle
│   ├── Dockerfile
│   └── src/main/java/com/example/loadtest/
│       ├── LoadTestApplication.java
│       ├── UserSimulator.java
│       └── MetricsReporter.java
├── monitoring/
│   ├── prometheus/
│   │   └── prometheus.yml
│   └── grafana/
│       ├── provisioning/
│       │   ├── datasources/datasource.yml
│       │   └── dashboards/dashboard.yml
│       └── dashboards/
│           └── nearby-friends.json
├── docs/
│   └── nearby-friends.drawio
└── README.md
```

## Docker Compose Services

| Service | Image/Build | Ports | Replicas |
|---------|-------------|-------|----------|
| nginx | nginx:alpine | 80 | 1 |
| postgresql | postgres:16 | 5432 | 1 |
| cassandra | cassandra:4 | 9042 | 1 |
| redis-cache | redis:7 | 6379 | 1 |
| redis-pubsub-1 | redis:7 | 6380 | 1 |
| redis-pubsub-2 | redis:7 | 6381 | 1 |
| redis-pubsub-3 | redis:7 | 6382 | 1 |
| zookeeper | zookeeper:3.9 | 2181 | 1 |
| zoonavigator | elkozmon/zoonavigator | 9000 | 1 |
| prometheus | prom/prometheus | 9090 | 1 |
| grafana | grafana/grafana | 3000 | 1 |
| api-gateway-1 | build: ./api-gateway | 8080 | 1 |
| api-gateway-2 | build: ./api-gateway | 8081 | 1 |
| user-service-1 | build: ./user-service | 8082 | 1 |
| user-service-2 | build: ./user-service | 8083 | 1 |
| location-service-1 | build: ./location-service | 8084 | 1 |
| location-service-2 | build: ./location-service | 8085 | 1 |
| websocket-service-1 | build: ./websocket-service | 8086 | 1 |
| websocket-service-2 | build: ./websocket-service | 8087 | 1 |
| load-client | build: ./load-client | — | profile: test |

## Load Test Client

- **Language:** Java 21, Spring Boot
- **WebSocket client:** Java-WebSocket or Spring WebSocket client
- **Simulates:** 1,000 users
- **Behavior:**
  1. Register 1,000 users via REST API
  2. Create friendship pairs (each user has 10-20 random friends)
  3. Open 1,000 WebSocket connections
  4. Each user sends location updates every 30s
  5. Locations clustered in 5-10 geographic areas so friends overlap within 5 miles
- **Metrics exposed to Prometheus:**
  - `loadtest.connections.active` — gauge
  - `loadtest.connections.failed` — counter
  - `loadtest.updates.sent` — counter
  - `loadtest.updates.received` — counter
  - `loadtest.latency.seconds` — histogram (p50/p95/p99)
- **Startup:** `docker compose --profile test up load-client`

## Monitoring

### Prometheus
Scrapes all Spring Boot services via `/actuator/prometheus` endpoint.

### Grafana Dashboards (pre-provisioned)
- **WebSocket Connections:** active connections per instance, connect/disconnect rate
- **Location Updates:** updates/sec published, updates/sec received
- **Pub/Sub Traffic:** messages per Redis node, channel count
- **Latency:** update propagation latency histogram
- **JVM Metrics:** heap, GC, threads per service
- **Load Test Results:** simulated users, sent/received ratio, latency percentiles

## Unit Tests (Spock)

### User Service
- `UserServiceSpec`: register, login (valid/invalid), duplicate username
- `JwtServiceSpec`: token generation, validation, expiry

### Location Service
- `LocationHistoryServiceSpec`: save location, query by time range, empty results

### WebSocket Service
- `DistanceCalculatorSpec`: Haversine correctness (known city pairs), edge cases (same point = 0, antipodal)
- `ConsistentHashRingSpec`: distribution uniformity, node add/remove, virtual nodes
- `NearbyFriendServiceSpec`: filter friends within radius, handle empty friend list, handle offline friends

## Error Handling

- WebSocket reconnection: client-side responsibility (load-client implements exponential backoff)
- Redis cache miss: user treated as offline (no location available)
- Pub/sub node failure: ZooKeeper watch triggers ring rebuild, affected channels re-subscribed
- Location Service down: location history writes are fire-and-forget; logged but don't block real-time flow
- User Service down: new WebSocket connections fail initialization, existing connections continue working
