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
