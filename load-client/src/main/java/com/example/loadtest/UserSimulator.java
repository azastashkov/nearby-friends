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
    private final MetricsReporter metrics;
    private final ScheduledExecutorService scheduler;

    private WebSocketClient wsClient;
    private double currentLat;
    private double currentLng;
    private ScheduledFuture<?> updateTask;

    public UserSimulator(String userId, String jwtToken, String wsUrl,
                          double clusterLat, double clusterLng,
                          MetricsReporter metrics, ScheduledExecutorService scheduler) {
        this.userId = userId;
        this.jwtToken = jwtToken;
        this.wsUrl = wsUrl;
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
                    updateTask = scheduler.scheduleAtFixedRate(
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
                            if (latency >= 0) metrics.recordLatency(Duration.ofMillis(latency));
                        }
                    } catch (Exception e) { log.debug("Failed to parse latency", e); }
                }

                @Override
                public void onClose(int code, String reason, boolean remote) {
                    metrics.connectionClosed();
                    if (remote) log.warn("Connection closed for user {}: {}", userId, reason);
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

    private synchronized void sendLocationUpdate() {
        if (wsClient == null || !wsClient.isOpen()) return;
        currentLat += (ThreadLocalRandom.current().nextDouble() - 0.5) * 0.002;
        currentLng += (ThreadLocalRandom.current().nextDouble() - 0.5) * 0.002;
        String json = gson.toJson(Map.of(
                "latitude", currentLat, "longitude", currentLng,
                "timestamp", System.currentTimeMillis()));
        wsClient.send(json);
        metrics.updateSent();
    }

    public void disconnect() {
        if (updateTask != null) updateTask.cancel(false);
        if (wsClient != null) wsClient.close();
    }
}
