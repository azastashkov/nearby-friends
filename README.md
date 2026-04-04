# Nearby Friends Service

Real-time microservice system that shows users which of their friends are geographically nearby (within 5 miles).

## Architecture

The system is composed of **4 Spring Boot microservices** behind an Nginx load balancer, communicating through REST APIs and Redis pub/sub with consistent hashing:

- **API Gateway** (x2) -- routes `/api/` REST traffic, validates JWT tokens, and forwards requests to User Service and Location Service.
- **User Service** (x2) -- manages user registration, authentication (JWT), and friend relationships backed by PostgreSQL.
- **Location Service** (x2) -- persists location history to Apache Cassandra, optimized for time-range queries.
- **WebSocket Service** (x2) -- maintains persistent WebSocket connections for real-time location updates. Caches current locations in Redis (TTL 30s), publishes location changes across instances via a 3-node Redis pub/sub cluster with consistent hashing (coordinated through ZooKeeper), retrieves friend lists from User Service, and asynchronously writes location history to Location Service.

Nginx uses **round-robin** for REST API traffic and **ip_hash** sticky sessions for WebSocket connections. A ZooKeeper-managed consistent hash ring determines which Redis pub/sub node handles each user's location channel, ensuring uniform distribution and minimal remapping when nodes change.

## Technology Stack

| Component | Technology |
|-----------|-----------|
| Language | Java 21 |
| Framework | Spring Boot 3.4.4 |
| Build | Gradle 8.10 (Groovy DSL) |
| Tests | Groovy (Spock Framework) |
| User DB | PostgreSQL 16 |
| Location History DB | Apache Cassandra 4 |
| Cache | Redis 7 |
| Pub/Sub | Redis 7 (3-node cluster) |
| Service Discovery | Apache ZooKeeper 3.9 |
| API Gateway | Spring Cloud Gateway |
| Load Balancer | Nginx |
| Auth | JWT (JJWT) |
| Monitoring | Prometheus + Grafana |
| Containerization | Docker Compose |

## Prerequisites

- Java 21
- Docker and Docker Compose
- Gradle 8.10 (or use included wrapper)

## Quick Start

### Build

```bash
./gradlew build
```

### Run

```bash
docker compose up -d
```

### Run Load Tests

```bash
docker compose --profile test up load-client
```

## Services

| Service | Port(s) | Description |
|---------|---------|-------------|
| Nginx | 80 | Load balancer |
| API Gateway | 8080, 8081 | Routes REST traffic, JWT validation |
| User Service | 8082, 8083 | User registration, login, friendships |
| Location Service | 8084, 8085 | Location history (Cassandra) |
| WebSocket Service | 8086, 8087 | Real-time location updates |
| PostgreSQL | 5432 | User data |
| Cassandra | 9042 | Location history |
| Redis Cache | 6379 | Current locations (TTL 30s) |
| Redis Pub/Sub | 6380-6382 | Location update channels |
| ZooKeeper | 2181 | Consistent hashing ring |
| Prometheus | 9090 | Metrics |
| Grafana | 3000 | Dashboards (admin/admin) |
| ZooNavigator | 9000 | ZooKeeper UI |

## API Documentation

### User Service

```
POST /api/users/register       - Register user (body: {"username", "password"})
POST /api/users/login          - Login (body: {"username", "password"}) -> {"userId", "token"}
GET  /api/users/{userId}       - Get profile
POST /api/users/{userId}/friends/{friendId}   - Add friend (requires Bearer token)
DELETE /api/users/{userId}/friends/{friendId} - Remove friend
GET  /api/users/{userId}/friends              - List friends -> {"friends": [...]}
```

### Location Service

```
POST /api/locations/history                         - Save location (body: {"userId", "latitude", "longitude"})
GET  /api/locations/history/{userId}?start=ISO&end=ISO - Query history
```

### WebSocket Protocol

Connect:

```
ws://localhost/ws/nearby?token=JWT
```

Client sends location (every 30s):

```json
{"latitude": 37.7749, "longitude": -122.4194, "timestamp": 1234567890}
```

Server sends on connect:

```json
{"type": "INIT", "nearbyFriends": [{"friendId": "...", "distance": 2.3, "timestamp": 1234567890}]}
```

Server sends when a friend is nearby:

```json
{"type": "NEARBY", "friendId": "...", "distance": 1.5, "timestamp": 1234567890}
```

Server sends when a friend leaves radius:

```json
{"type": "REMOVED", "friendId": "..."}
```

## Monitoring

- **Grafana:** http://localhost:3000 (admin/admin)
- **Prometheus:** http://localhost:9090
- **ZooNavigator:** http://localhost:9000

## Architecture Decisions

- **Lettuce over Jedis** for Redis pub/sub -- non-blocking, multiplexed connections fit the reactive WebSocket model.
- **Consistent hashing** for pub/sub node selection -- uniform distribution across 3 Redis nodes with minimal remapping when the topology changes.
- **Fire-and-forget** for location history writes -- asynchronous POSTs to Location Service avoid blocking the real-time WebSocket flow.
- **ConcurrentWebSocketSessionDecorator** for thread-safe WebSocket sends -- prevents concurrent write exceptions when multiple pub/sub callbacks target the same session.
- **IP hash in Nginx** for WebSocket sticky sessions -- ensures a client's WebSocket connection always routes to the same service instance, avoiding mid-session handoff.
