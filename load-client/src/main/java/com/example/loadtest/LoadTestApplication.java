package com.example.loadtest;

import com.google.gson.Gson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.*;
import java.util.concurrent.*;

@SpringBootApplication
public class LoadTestApplication implements CommandLineRunner {
    private static final Logger log = LoggerFactory.getLogger(LoadTestApplication.class);

    private static final double[][] CLUSTER_CENTERS = {
            {37.7749, -122.4194}, {37.8044, -122.2712}, {37.7849, -122.4094},
            {34.0522, -118.2437}, {34.0622, -118.2337},
            {40.7128, -74.0060}, {40.7228, -74.0160},
            {41.8781, -87.6298}, {47.6062, -122.3321}, {25.7617, -80.1918}
    };

    @Value("${api.base-url}") private String apiBaseUrl;
    @Value("${websocket.url}") private String wsUrl;
    @Value("${simulated.users:1000}") private int simulatedUsers;

    private final MetricsReporter metrics;

    public LoadTestApplication(MetricsReporter metrics) { this.metrics = metrics; }

    public static void main(String[] args) { SpringApplication.run(LoadTestApplication.class, args); }

    @Override
    public void run(String... args) throws Exception {
        WebClient webClient = WebClient.builder().baseUrl(apiBaseUrl).build();

        log.info("Phase 1: Registering {} users...", simulatedUsers);
        Map<String, String> usernames = new LinkedHashMap<>();
        for (int i = 0; i < simulatedUsers; i++) {
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> response = webClient.post()
                        .uri("/api/users/register")
                        .bodyValue(Map.of("username", "loadtest_user_" + i, "password", "pass_" + i))
                        .retrieve().bodyToMono(Map.class).block();
                usernames.put(response.get("id").toString(), "loadtest_user_" + i);
            } catch (Exception e) { log.warn("Failed to register user {}: {}", i, e.getMessage()); }
        }
        log.info("Registered {} users", usernames.size());

        log.info("Phase 2: Logging in...");
        Map<String, String> tokens = new LinkedHashMap<>();
        int idx = 0;
        for (var entry : usernames.entrySet()) {
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> response = webClient.post()
                        .uri("/api/users/login")
                        .bodyValue(Map.of("username", entry.getValue(), "password", "pass_" + idx))
                        .retrieve().bodyToMono(Map.class).block();
                tokens.put(entry.getKey(), response.get("token").toString());
            } catch (Exception e) { log.warn("Failed to login {}: {}", entry.getValue(), e.getMessage()); }
            idx++;
        }
        log.info("Collected {} tokens", tokens.size());

        log.info("Phase 3: Creating friendships...");
        List<String> userIdList = new ArrayList<>(usernames.keySet());
        Random random = new Random(42);
        for (int i = 0; i < userIdList.size(); i++) {
            int friendCount = 10 + random.nextInt(11);
            for (int j = 0; j < friendCount; j++) {
                int friendIdx = random.nextInt(userIdList.size());
                if (friendIdx == i) continue;
                try {
                    webClient.post()
                            .uri("/api/users/" + userIdList.get(i) + "/friends/" + userIdList.get(friendIdx))
                            .header("Authorization", "Bearer " + tokens.get(userIdList.get(i)))
                            .retrieve().bodyToMono(Void.class).block();
                } catch (Exception e) { /* duplicate, ignore */ }
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
        log.info("All connections established. Load test running.");
        Thread.currentThread().join();
    }
}
