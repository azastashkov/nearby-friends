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
