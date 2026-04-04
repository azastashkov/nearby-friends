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

    public LocationCacheService(StringRedisTemplate redisTemplate, ObjectMapper objectMapper,
                                 @Value("${nearby.cache-ttl-seconds}") long ttlSeconds) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.ttlSeconds = ttlSeconds;
    }

    public void updateLocation(String userId, LocationUpdate update) {
        try {
            String json = objectMapper.writeValueAsString(update);
            redisTemplate.opsForValue().set(KEY_PREFIX + userId, json, Duration.ofSeconds(ttlSeconds));
        } catch (JsonProcessingException e) { throw new RuntimeException("Failed to serialize location", e); }
    }

    public Optional<LocationUpdate> getLocation(String userId) {
        String json = redisTemplate.opsForValue().get(KEY_PREFIX + userId);
        if (json == null) return Optional.empty();
        try { return Optional.of(objectMapper.readValue(json, LocationUpdate.class)); }
        catch (JsonProcessingException e) { return Optional.empty(); }
    }
}
