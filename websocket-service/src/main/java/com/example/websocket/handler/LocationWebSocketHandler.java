package com.example.websocket.handler;

import com.example.websocket.dto.LocationUpdate;
import com.example.websocket.service.NearbyFriendService;
import com.example.websocket.service.PubSubService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class LocationWebSocketHandler extends TextWebSocketHandler {
    private static final Logger log = LoggerFactory.getLogger(LocationWebSocketHandler.class);

    private final NearbyFriendService nearbyFriendService;
    private final PubSubService pubSubService;
    private final WebClient userServiceClient;
    private final WebClient locationServiceClient;
    private final ObjectMapper objectMapper;
    private final SecretKey jwtKey;
    private final ConcurrentHashMap<String, SessionState> sessions = new ConcurrentHashMap<>();
    private final AtomicInteger activeConnections;
    private final Counter connectionsTotal;

    private static class SessionState {
        final String userId;
        final ConcurrentWebSocketSessionDecorator session;
        volatile LocationUpdate lastLocation;
        final Set<String> subscribedFriends = ConcurrentHashMap.newKeySet();
        final Set<String> nearbyFriendIds = ConcurrentHashMap.newKeySet();

        SessionState(String userId, ConcurrentWebSocketSessionDecorator session) {
            this.userId = userId;
            this.session = session;
        }
    }

    public LocationWebSocketHandler(NearbyFriendService nearbyFriendService,
                                     PubSubService pubSubService,
                                     WebClient.Builder webClientBuilder,
                                     ObjectMapper objectMapper,
                                     MeterRegistry meterRegistry,
                                     @Value("${jwt.secret}") String jwtSecret,
                                     @Value("${services.user-service-url}") String userServiceUrl,
                                     @Value("${services.location-service-url}") String locationServiceUrl) {
        this.nearbyFriendService = nearbyFriendService;
        this.pubSubService = pubSubService;
        this.userServiceClient = webClientBuilder.clone().baseUrl(userServiceUrl).build();
        this.locationServiceClient = webClientBuilder.clone().baseUrl(locationServiceUrl).build();
        this.objectMapper = objectMapper;
        this.jwtKey = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
        this.activeConnections = meterRegistry.gauge("ws.connections.active", new AtomicInteger(0));
        this.connectionsTotal = meterRegistry.counter("ws.connections.total");
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession rawSession) throws Exception {
        String token = extractToken(rawSession.getUri());
        if (token == null) { rawSession.close(CloseStatus.NOT_ACCEPTABLE); return; }

        Claims claims;
        try { claims = Jwts.parser().verifyWith(jwtKey).build().parseSignedClaims(token).getPayload(); }
        catch (Exception e) { rawSession.close(CloseStatus.NOT_ACCEPTABLE); return; }

        String userId = claims.getSubject();
        var session = new ConcurrentWebSocketSessionDecorator(rawSession, 5000, 65536);
        var state = new SessionState(userId, session);
        sessions.put(rawSession.getId(), state);
        activeConnections.incrementAndGet();
        connectionsTotal.increment();

        List<String> friendIds = fetchFriendIds(userId);
        for (String friendId : friendIds) {
            state.subscribedFriends.add(friendId);
            pubSubService.subscribe(friendId, createFriendListener(rawSession.getId(), friendId));
        }
        log.info("WebSocket connected: userId={}, friends={}", userId, friendIds.size());
    }

    @Override
    protected void handleTextMessage(WebSocketSession rawSession, TextMessage message) throws Exception {
        SessionState state = sessions.get(rawSession.getId());
        if (state == null) return;

        LocationUpdate update = objectMapper.readValue(message.getPayload(), LocationUpdate.class);
        boolean isFirst = (state.lastLocation == null);
        state.lastLocation = update;

        nearbyFriendService.processLocationUpdate(state.userId, update);
        saveToLocationServiceAsync(state.userId, update);

        if (isFirst) {
            var nearbyFriends = nearbyFriendService.computeNearbyFriends(
                    new ArrayList<>(state.subscribedFriends), update);
            for (var nf : nearbyFriends) state.nearbyFriendIds.add(nf.friendId());
            String json = objectMapper.writeValueAsString(Map.of("type", "INIT", "nearbyFriends", nearbyFriends));
            state.session.sendMessage(new TextMessage(json));
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession rawSession, CloseStatus status) {
        SessionState state = sessions.remove(rawSession.getId());
        if (state != null) {
            activeConnections.decrementAndGet();
            for (String friendId : state.subscribedFriends) pubSubService.unsubscribe(friendId);
            log.info("WebSocket disconnected: userId={}", state.userId);
        }
    }

    private MessageListener createFriendListener(String sessionId, String friendId) {
        return (Message message, byte[] pattern) -> {
            try {
                SessionState state = sessions.get(sessionId);
                if (state == null || state.lastLocation == null) return;
                LocationUpdate friendLocation = objectMapper.readValue(message.getBody(), LocationUpdate.class);
                var result = nearbyFriendService.evaluateFriendUpdate(state.lastLocation, friendId, friendLocation);
                if (result.isPresent()) {
                    state.nearbyFriendIds.add(friendId);
                    String json = objectMapper.writeValueAsString(Map.of("type", "NEARBY",
                            "friendId", friendId, "distance", result.get().distance(),
                            "timestamp", result.get().timestamp()));
                    state.session.sendMessage(new TextMessage(json));
                } else if (state.nearbyFriendIds.remove(friendId)) {
                    String json = objectMapper.writeValueAsString(Map.of("type", "REMOVED", "friendId", friendId));
                    state.session.sendMessage(new TextMessage(json));
                }
            } catch (IOException e) { log.error("Error processing friend location update", e); }
        };
    }

    @SuppressWarnings("unchecked")
    private List<String> fetchFriendIds(String userId) {
        try {
            Map<String, Object> response = userServiceClient.get()
                    .uri("/{userId}/friends", userId).retrieve().bodyToMono(Map.class).block();
            if (response != null && response.containsKey("friends"))
                return ((List<?>) response.get("friends")).stream().map(Object::toString).toList();
        } catch (Exception e) { log.error("Failed to fetch friend list for userId={}", userId, e); }
        return List.of();
    }

    private void saveToLocationServiceAsync(String userId, LocationUpdate update) {
        locationServiceClient.post().uri("/api/locations/history")
                .bodyValue(Map.of("userId", userId, "latitude", update.latitude(), "longitude", update.longitude()))
                .retrieve().bodyToMono(Void.class)
                .subscribe(null, e -> log.warn("Failed to save location history", e));
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
